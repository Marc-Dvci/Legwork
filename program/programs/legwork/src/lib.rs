//! Legwork: paid, verified real-world missions on Solana.
//!
//! The program is the mission board. A creator funds a mission in USDC; the
//! funds sit in a vault owned by the mission account. A verifier oracle
//! (GPS + camera + Gemini vision, run by the Legwork service) approves a
//! completion, which pays the worker straight from the vault and records a
//! proof hash on chain. Worker reputation, streaks and SKR stake live in a
//! per-wallet account so any client can read them.

use anchor_lang::prelude::*;
use anchor_spl::{
    associated_token::AssociatedToken,
    token_interface::{self, Mint, TokenAccount, TokenInterface, TransferChecked},
};

declare_id!("86V9Vw6jPnoy4R6feJK6atwMK3x1oUootvvbqb8iZHD6");

pub const CONFIG_SEED: &[u8] = b"config";
pub const MISSION_SEED: &[u8] = b"mission";
pub const WORKER_SEED: &[u8] = b"worker";
pub const CREATOR_SEED: &[u8] = b"creator";
pub const RESERVATION_SEED: &[u8] = b"reservation";
pub const COMPLETION_SEED: &[u8] = b"completion";
pub const DEVICE_SEED: &[u8] = b"device";
pub const SKR_VAULT_SEED: &[u8] = b"skr_vault";

/// A reservation holds a slot for the worker for this long.
pub const RESERVATION_SECONDS: i64 = 45 * 60;
/// Reputation points granted per approved completion, before the confidence bonus.
pub const SCORE_PER_APPROVAL: u32 = 10;

#[program]
pub mod legwork {
    use super::*;

    /// One-time setup: verifier key, token mints, fee schedule and the SKR stake vault.
    pub fn initialize(ctx: Context<Initialize>, fee_bps: u16, creator_stake_threshold: u64) -> Result<()> {
        require!(fee_bps <= 2_000, LegworkError::FeeTooHigh);
        let config = &mut ctx.accounts.config;
        config.authority = ctx.accounts.authority.key();
        config.verifier = ctx.accounts.verifier.key();
        config.usdc_mint = ctx.accounts.usdc_mint.key();
        config.skr_mint = ctx.accounts.skr_mint.key();
        config.treasury = ctx.accounts.treasury.key();
        config.fee_bps = fee_bps;
        config.creator_stake_threshold = creator_stake_threshold;
        config.missions_created = 0;
        config.bump = ctx.bumps.config;
        Ok(())
    }

    /// Creator publishes and funds a mission in one signature.
    /// `reward * slots` USDC moves into the mission vault; the platform fee goes to the treasury.
    pub fn create_mission(ctx: Context<CreateMission>, args: CreateMissionArgs) -> Result<()> {
        require!(args.reward > 0, LegworkError::InvalidReward);
        require!(args.slots > 0, LegworkError::InvalidSlots);
        require!(args.radius_m >= 10 && args.radius_m <= 2_000, LegworkError::InvalidRadius);
        require!(args.title.len() <= Mission::TITLE_LEN, LegworkError::TextTooLong);
        require!(args.instructions.len() <= Mission::INSTRUCTIONS_LEN, LegworkError::TextTooLong);
        require!(args.question.len() <= Mission::QUESTION_LEN, LegworkError::TextTooLong);
        require!(args.lat_e6.abs() <= 90_000_000 && args.lon_e6.abs() <= 180_000_000, LegworkError::InvalidLocation);
        let now = Clock::get()?.unix_timestamp;
        require!(args.deadline > now, LegworkError::DeadlinePassed);

        let config = &mut ctx.accounts.config;
        let creator = &mut ctx.accounts.creator_profile;
        if creator.wallet == Pubkey::default() {
            creator.wallet = ctx.accounts.creator.key();
            creator.bump = ctx.bumps.creator_profile;
        }

        let escrow = args
            .reward
            .checked_mul(args.slots as u64)
            .ok_or(LegworkError::MathOverflow)?;
        // Creators who stake SKR above the threshold pay half the platform fee.
        let fee_bps = if creator.skr_staked >= config.creator_stake_threshold {
            config.fee_bps / 2
        } else {
            config.fee_bps
        };
        let fee = escrow
            .checked_mul(fee_bps as u64)
            .ok_or(LegworkError::MathOverflow)?
            / 10_000;

        let decimals = ctx.accounts.usdc_mint.decimals;
        token_interface::transfer_checked(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.creator_usdc.to_account_info(),
                    mint: ctx.accounts.usdc_mint.to_account_info(),
                    to: ctx.accounts.vault.to_account_info(),
                    authority: ctx.accounts.creator.to_account_info(),
                },
            ),
            escrow,
            decimals,
        )?;
        if fee > 0 {
            token_interface::transfer_checked(
                CpiContext::new(
                    ctx.accounts.token_program.key(),
                    TransferChecked {
                        from: ctx.accounts.creator_usdc.to_account_info(),
                        mint: ctx.accounts.usdc_mint.to_account_info(),
                        to: ctx.accounts.treasury.to_account_info(),
                        authority: ctx.accounts.creator.to_account_info(),
                    },
                ),
                fee,
                decimals,
            )?;
        }

        let mission = &mut ctx.accounts.mission;
        mission.id = config.missions_created;
        mission.creator = ctx.accounts.creator.key();
        mission.vault = ctx.accounts.vault.key();
        mission.reward = args.reward;
        mission.slots = args.slots;
        mission.filled = 0;
        mission.lat_e6 = args.lat_e6;
        mission.lon_e6 = args.lon_e6;
        mission.radius_m = args.radius_m;
        mission.deadline = args.deadline;
        mission.requires_seeker = args.requires_seeker;
        mission.min_score = args.min_score;
        mission.category = args.category;
        mission.proof_kind = args.proof_kind;
        mission.status = MissionStatus::Open as u8;
        mission.created_at = now;
        mission.fee_paid = fee;
        mission.title = args.title;
        mission.instructions = args.instructions;
        mission.question = args.question;
        mission.bump = ctx.bumps.mission;

        config.missions_created = config.missions_created.checked_add(1).ok_or(LegworkError::MathOverflow)?;
        creator.missions = creator.missions.saturating_add(1);
        creator.total_funded = creator.total_funded.saturating_add(escrow);

        emit!(MissionCreated {
            mission: mission.key(),
            id: mission.id,
            creator: mission.creator,
            reward: mission.reward,
            slots: mission.slots,
        });
        Ok(())
    }

    /// Verifier holds a slot for a worker who tapped "Start mission". Gasless for the worker.
    pub fn reserve(ctx: Context<Reserve>) -> Result<()> {
        let mission = &ctx.accounts.mission;
        let now = Clock::get()?.unix_timestamp;
        require!(mission.status == MissionStatus::Open as u8, LegworkError::MissionClosed);
        require!(mission.filled < mission.slots, LegworkError::MissionFull);
        require!(now < mission.deadline, LegworkError::DeadlinePassed);

        let reservation = &mut ctx.accounts.reservation;
        reservation.mission = mission.key();
        reservation.worker = ctx.accounts.worker.key();
        reservation.expires_at = now + RESERVATION_SECONDS;
        reservation.bump = ctx.bumps.reservation;
        emit!(Reserved { mission: mission.key(), worker: reservation.worker, expires_at: reservation.expires_at });
        Ok(())
    }

    /// Verifier approves a completion: pays the worker from the vault, records the proof hash,
    /// updates reputation and streak, and closes the reservation.
    pub fn approve_completion(
        ctx: Context<ApproveCompletion>,
        proof_hash: [u8; 32],
        confidence: u8,
        answer: u8,
    ) -> Result<()> {
        let now = Clock::get()?.unix_timestamp;
        let mission = &mut ctx.accounts.mission;
        require!(mission.status == MissionStatus::Open as u8, LegworkError::MissionClosed);
        require!(mission.filled < mission.slots, LegworkError::MissionFull);
        require!(now < mission.deadline, LegworkError::DeadlinePassed);

        let profile = &mut ctx.accounts.worker_profile;
        if profile.wallet == Pubkey::default() {
            profile.wallet = ctx.accounts.worker.key();
            profile.bump = ctx.bumps.worker_profile;
        }
        require!(profile.score >= mission.min_score as u32, LegworkError::ScoreTooLow);
        if mission.requires_seeker {
            require!(profile.seeker_verified, LegworkError::SeekerRequired);
        }

        let amount = mission.reward;
        let id_bytes = mission.id.to_le_bytes();
        let signer_seeds: &[&[&[u8]]] = &[&[MISSION_SEED, id_bytes.as_ref(), &[mission.bump]]];
        token_interface::transfer_checked(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.vault.to_account_info(),
                    mint: ctx.accounts.usdc_mint.to_account_info(),
                    to: ctx.accounts.worker_usdc.to_account_info(),
                    authority: mission.to_account_info(),
                },
                signer_seeds,
            ),
            amount,
            ctx.accounts.usdc_mint.decimals,
        )?;

        mission.filled = mission.filled.saturating_add(1);
        if mission.filled == mission.slots {
            mission.status = MissionStatus::Filled as u8;
        }

        let day = (now / 86_400) as u32;
        profile.streak = if day == profile.last_day {
            profile.streak.max(1)
        } else if day == profile.last_day.saturating_add(1) {
            profile.streak.saturating_add(1)
        } else {
            1
        };
        profile.last_day = day;
        profile.approved = profile.approved.saturating_add(1);
        profile.total_earned = profile.total_earned.saturating_add(amount);
        profile.score = profile
            .score
            .saturating_add(SCORE_PER_APPROVAL + (confidence as u32) / 10);
        profile.categories |= 1u16 << (mission.category as u16 & 15);

        let completion = &mut ctx.accounts.completion;
        completion.mission = mission.key();
        completion.worker = ctx.accounts.worker.key();
        completion.proof_hash = proof_hash;
        completion.confidence = confidence;
        completion.answer = answer;
        completion.amount = amount;
        completion.approved_at = now;
        completion.bump = ctx.bumps.completion;

        let creator = &mut ctx.accounts.creator_profile;
        creator.total_paid = creator.total_paid.saturating_add(amount);

        emit!(Completed {
            mission: mission.key(),
            worker: completion.worker,
            amount,
            proof_hash,
            confidence,
            answer,
        });
        Ok(())
    }

    /// Verifier records a rejected proof so approval rate is readable on chain.
    pub fn record_rejection(ctx: Context<RecordRejection>) -> Result<()> {
        let profile = &mut ctx.accounts.worker_profile;
        if profile.wallet == Pubkey::default() {
            profile.wallet = ctx.accounts.worker.key();
            profile.bump = ctx.bumps.worker_profile;
        }
        profile.rejected = profile.rejected.saturating_add(1);
        Ok(())
    }

    /// Verifier marks a worker as a verified Seeker owner. One SGT mint maps to one worker,
    /// which is the anti-sybil rule for Seeker-only missions.
    pub fn verify_seeker(ctx: Context<VerifySeeker>, sgt_mint: Pubkey) -> Result<()> {
        let device = &mut ctx.accounts.device;
        if device.worker != Pubkey::default() {
            require_keys_eq!(device.worker, ctx.accounts.worker.key(), LegworkError::DeviceAlreadyBound);
        }
        device.sgt_mint = sgt_mint;
        device.worker = ctx.accounts.worker.key();
        device.bump = ctx.bumps.device;

        let profile = &mut ctx.accounts.worker_profile;
        if profile.wallet == Pubkey::default() {
            profile.wallet = ctx.accounts.worker.key();
            profile.bump = ctx.bumps.worker_profile;
        }
        profile.seeker_verified = true;
        profile.sgt_mint = sgt_mint;
        Ok(())
    }

    /// Worker locks SKR behind their profile. Stake raises the trust tier and unlocks missions.
    pub fn stake_skr(ctx: Context<StakeSkr>, amount: u64) -> Result<()> {
        require!(amount > 0, LegworkError::InvalidAmount);
        token_interface::transfer_checked(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.worker_skr.to_account_info(),
                    mint: ctx.accounts.skr_mint.to_account_info(),
                    to: ctx.accounts.skr_vault.to_account_info(),
                    authority: ctx.accounts.worker.to_account_info(),
                },
            ),
            amount,
            ctx.accounts.skr_mint.decimals,
        )?;
        let profile = &mut ctx.accounts.worker_profile;
        if profile.wallet == Pubkey::default() {
            profile.wallet = ctx.accounts.worker.key();
            profile.bump = ctx.bumps.worker_profile;
        }
        profile.skr_staked = profile.skr_staked.checked_add(amount).ok_or(LegworkError::MathOverflow)?;
        emit!(Staked { wallet: profile.wallet, amount, total: profile.skr_staked, role: 0 });
        Ok(())
    }

    pub fn unstake_skr(ctx: Context<StakeSkr>, amount: u64) -> Result<()> {
        let profile = &mut ctx.accounts.worker_profile;
        require!(amount > 0 && amount <= profile.skr_staked, LegworkError::InvalidAmount);
        profile.skr_staked -= amount;
        let bump = ctx.accounts.config.bump;
        let signer_seeds: &[&[&[u8]]] = &[&[CONFIG_SEED, &[bump]]];
        token_interface::transfer_checked(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.skr_vault.to_account_info(),
                    mint: ctx.accounts.skr_mint.to_account_info(),
                    to: ctx.accounts.worker_skr.to_account_info(),
                    authority: ctx.accounts.config.to_account_info(),
                },
                signer_seeds,
            ),
            amount,
            ctx.accounts.skr_mint.decimals,
        )?;
        emit!(Staked { wallet: profile.wallet, amount, total: profile.skr_staked, role: 0 });
        Ok(())
    }

    /// Creator locks SKR to halve the platform fee on every mission they fund.
    pub fn creator_stake_skr(ctx: Context<CreatorStakeSkr>, amount: u64) -> Result<()> {
        require!(amount > 0, LegworkError::InvalidAmount);
        token_interface::transfer_checked(
            CpiContext::new(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.creator_skr.to_account_info(),
                    mint: ctx.accounts.skr_mint.to_account_info(),
                    to: ctx.accounts.skr_vault.to_account_info(),
                    authority: ctx.accounts.creator.to_account_info(),
                },
            ),
            amount,
            ctx.accounts.skr_mint.decimals,
        )?;
        let profile = &mut ctx.accounts.creator_profile;
        if profile.wallet == Pubkey::default() {
            profile.wallet = ctx.accounts.creator.key();
            profile.bump = ctx.bumps.creator_profile;
        }
        profile.skr_staked = profile.skr_staked.checked_add(amount).ok_or(LegworkError::MathOverflow)?;
        emit!(Staked { wallet: profile.wallet, amount, total: profile.skr_staked, role: 1 });
        Ok(())
    }

    pub fn creator_unstake_skr(ctx: Context<CreatorStakeSkr>, amount: u64) -> Result<()> {
        let profile = &mut ctx.accounts.creator_profile;
        require!(amount > 0 && amount <= profile.skr_staked, LegworkError::InvalidAmount);
        profile.skr_staked -= amount;
        let bump = ctx.accounts.config.bump;
        let signer_seeds: &[&[&[u8]]] = &[&[CONFIG_SEED, &[bump]]];
        token_interface::transfer_checked(
            CpiContext::new_with_signer(
                ctx.accounts.token_program.key(),
                TransferChecked {
                    from: ctx.accounts.skr_vault.to_account_info(),
                    mint: ctx.accounts.skr_mint.to_account_info(),
                    to: ctx.accounts.creator_skr.to_account_info(),
                    authority: ctx.accounts.config.to_account_info(),
                },
                signer_seeds,
            ),
            amount,
            ctx.accounts.skr_mint.decimals,
        )?;
        emit!(Staked { wallet: profile.wallet, amount, total: profile.skr_staked, role: 1 });
        Ok(())
    }

    /// Creator closes a mission and takes back whatever is left in the vault.
    pub fn close_mission(ctx: Context<CloseMission>) -> Result<()> {
        let mission = &mut ctx.accounts.mission;
        require!(mission.status != MissionStatus::Closed as u8, LegworkError::MissionClosed);
        let remaining = ctx.accounts.vault.amount;
        if remaining > 0 {
            let id_bytes = mission.id.to_le_bytes();
            let signer_seeds: &[&[&[u8]]] = &[&[MISSION_SEED, id_bytes.as_ref(), &[mission.bump]]];
            token_interface::transfer_checked(
                CpiContext::new_with_signer(
                    ctx.accounts.token_program.key(),
                    TransferChecked {
                        from: ctx.accounts.vault.to_account_info(),
                        mint: ctx.accounts.usdc_mint.to_account_info(),
                        to: ctx.accounts.creator_usdc.to_account_info(),
                        authority: mission.to_account_info(),
                    },
                    signer_seeds,
                ),
                remaining,
                ctx.accounts.usdc_mint.decimals,
            )?;
        }
        mission.status = MissionStatus::Closed as u8;
        Ok(())
    }
}

// ---------------------------------------------------------------------------
// Instruction arguments
// ---------------------------------------------------------------------------

#[derive(AnchorSerialize, AnchorDeserialize, Clone)]
pub struct CreateMissionArgs {
    pub reward: u64,
    pub slots: u16,
    pub lat_e6: i32,
    pub lon_e6: i32,
    pub radius_m: u16,
    pub deadline: i64,
    pub requires_seeker: bool,
    pub min_score: u16,
    pub category: u8,
    pub proof_kind: u8,
    pub title: String,
    pub instructions: String,
    pub question: String,
}

// ---------------------------------------------------------------------------
// Accounts
// ---------------------------------------------------------------------------

#[derive(Accounts)]
pub struct Initialize<'info> {
    #[account(mut)]
    pub authority: Signer<'info>,
    /// CHECK: any key; it is the oracle that signs approvals.
    pub verifier: UncheckedAccount<'info>,
    #[account(
        init,
        payer = authority,
        space = 8 + Config::INIT_SPACE,
        seeds = [CONFIG_SEED],
        bump
    )]
    pub config: Box<Account<'info, Config>>,
    pub usdc_mint: Box<InterfaceAccount<'info, Mint>>,
    pub skr_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(token::mint = usdc_mint)]
    pub treasury: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(
        init,
        payer = authority,
        associated_token::mint = skr_mint,
        associated_token::authority = config,
        associated_token::token_program = skr_token_program,
    )]
    pub skr_vault: Box<InterfaceAccount<'info, TokenAccount>>,
    pub skr_token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct CreateMission<'info> {
    #[account(mut)]
    pub creator: Signer<'info>,
    #[account(mut, seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    #[account(
        init_if_needed,
        payer = creator,
        space = 8 + CreatorProfile::INIT_SPACE,
        seeds = [CREATOR_SEED, creator.key().as_ref()],
        bump
    )]
    pub creator_profile: Box<Account<'info, CreatorProfile>>,
    #[account(
        init,
        payer = creator,
        space = 8 + Mission::INIT_SPACE,
        seeds = [MISSION_SEED, config.missions_created.to_le_bytes().as_ref()],
        bump
    )]
    pub mission: Box<Account<'info, Mission>>,
    #[account(address = config.usdc_mint)]
    pub usdc_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(
        init,
        payer = creator,
        associated_token::mint = usdc_mint,
        associated_token::authority = mission,
        associated_token::token_program = token_program,
    )]
    pub vault: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(mut, token::mint = usdc_mint, token::authority = creator)]
    pub creator_usdc: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(mut, address = config.treasury)]
    pub treasury: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct Reserve<'info> {
    #[account(mut, address = config.verifier @ LegworkError::NotVerifier)]
    pub verifier: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    #[account(seeds = [MISSION_SEED, mission.id.to_le_bytes().as_ref()], bump = mission.bump)]
    pub mission: Box<Account<'info, Mission>>,
    /// CHECK: the worker's wallet; it does not sign, the verifier vouches for the request.
    pub worker: UncheckedAccount<'info>,
    #[account(
        init_if_needed,
        payer = verifier,
        space = 8 + Reservation::INIT_SPACE,
        seeds = [RESERVATION_SEED, mission.key().as_ref(), worker.key().as_ref()],
        bump
    )]
    pub reservation: Box<Account<'info, Reservation>>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct ApproveCompletion<'info> {
    #[account(mut, address = config.verifier @ LegworkError::NotVerifier)]
    pub verifier: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    #[account(mut, seeds = [MISSION_SEED, mission.id.to_le_bytes().as_ref()], bump = mission.bump)]
    pub mission: Box<Account<'info, Mission>>,
    #[account(mut, address = mission.vault)]
    pub vault: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(
        mut,
        seeds = [CREATOR_SEED, mission.creator.as_ref()],
        bump = creator_profile.bump
    )]
    pub creator_profile: Box<Account<'info, CreatorProfile>>,
    /// CHECK: the paid wallet; the verifier attests the proof came from it.
    pub worker: UncheckedAccount<'info>,
    #[account(
        init_if_needed,
        payer = verifier,
        space = 8 + WorkerProfile::INIT_SPACE,
        seeds = [WORKER_SEED, worker.key().as_ref()],
        bump
    )]
    pub worker_profile: Box<Account<'info, WorkerProfile>>,
    #[account(
        mut,
        seeds = [RESERVATION_SEED, mission.key().as_ref(), worker.key().as_ref()],
        bump = reservation.bump,
        close = verifier
    )]
    pub reservation: Box<Account<'info, Reservation>>,
    #[account(
        init,
        payer = verifier,
        space = 8 + Completion::INIT_SPACE,
        seeds = [COMPLETION_SEED, mission.key().as_ref(), worker.key().as_ref()],
        bump
    )]
    pub completion: Box<Account<'info, Completion>>,
    #[account(address = config.usdc_mint)]
    pub usdc_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(
        init_if_needed,
        payer = verifier,
        associated_token::mint = usdc_mint,
        associated_token::authority = worker,
        associated_token::token_program = token_program,
    )]
    pub worker_usdc: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
    pub associated_token_program: Program<'info, AssociatedToken>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct RecordRejection<'info> {
    #[account(mut, address = config.verifier @ LegworkError::NotVerifier)]
    pub verifier: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    /// CHECK: wallet whose profile is updated.
    pub worker: UncheckedAccount<'info>,
    #[account(
        init_if_needed,
        payer = verifier,
        space = 8 + WorkerProfile::INIT_SPACE,
        seeds = [WORKER_SEED, worker.key().as_ref()],
        bump
    )]
    pub worker_profile: Box<Account<'info, WorkerProfile>>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
#[instruction(sgt_mint: Pubkey)]
pub struct VerifySeeker<'info> {
    #[account(mut, address = config.verifier @ LegworkError::NotVerifier)]
    pub verifier: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    /// CHECK: wallet that holds the Seeker Genesis Token.
    pub worker: UncheckedAccount<'info>,
    #[account(
        init_if_needed,
        payer = verifier,
        space = 8 + WorkerProfile::INIT_SPACE,
        seeds = [WORKER_SEED, worker.key().as_ref()],
        bump
    )]
    pub worker_profile: Box<Account<'info, WorkerProfile>>,
    #[account(
        init_if_needed,
        payer = verifier,
        space = 8 + Device::INIT_SPACE,
        seeds = [DEVICE_SEED, sgt_mint.as_ref()],
        bump
    )]
    pub device: Box<Account<'info, Device>>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct StakeSkr<'info> {
    #[account(mut)]
    pub worker: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    #[account(
        init_if_needed,
        payer = worker,
        space = 8 + WorkerProfile::INIT_SPACE,
        seeds = [WORKER_SEED, worker.key().as_ref()],
        bump
    )]
    pub worker_profile: Box<Account<'info, WorkerProfile>>,
    #[account(address = config.skr_mint)]
    pub skr_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(mut, token::mint = skr_mint, token::authority = worker)]
    pub worker_skr: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(
        mut,
        associated_token::mint = skr_mint,
        associated_token::authority = config,
        associated_token::token_program = token_program,
    )]
    pub skr_vault: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct CreatorStakeSkr<'info> {
    #[account(mut)]
    pub creator: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    #[account(
        init_if_needed,
        payer = creator,
        space = 8 + CreatorProfile::INIT_SPACE,
        seeds = [CREATOR_SEED, creator.key().as_ref()],
        bump
    )]
    pub creator_profile: Box<Account<'info, CreatorProfile>>,
    #[account(address = config.skr_mint)]
    pub skr_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(mut, token::mint = skr_mint, token::authority = creator)]
    pub creator_skr: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(
        mut,
        associated_token::mint = skr_mint,
        associated_token::authority = config,
        associated_token::token_program = token_program,
    )]
    pub skr_vault: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
    pub system_program: Program<'info, System>,
}

#[derive(Accounts)]
pub struct CloseMission<'info> {
    #[account(mut)]
    pub creator: Signer<'info>,
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,
    #[account(
        mut,
        seeds = [MISSION_SEED, mission.id.to_le_bytes().as_ref()],
        bump = mission.bump,
        has_one = creator @ LegworkError::NotCreator
    )]
    pub mission: Box<Account<'info, Mission>>,
    #[account(mut, address = mission.vault)]
    pub vault: Box<InterfaceAccount<'info, TokenAccount>>,
    #[account(address = config.usdc_mint)]
    pub usdc_mint: Box<InterfaceAccount<'info, Mint>>,
    #[account(mut, token::mint = usdc_mint, token::authority = creator)]
    pub creator_usdc: Box<InterfaceAccount<'info, TokenAccount>>,
    pub token_program: Interface<'info, TokenInterface>,
}

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

#[account]
#[derive(InitSpace)]
pub struct Config {
    pub authority: Pubkey,
    pub verifier: Pubkey,
    pub usdc_mint: Pubkey,
    pub skr_mint: Pubkey,
    pub treasury: Pubkey,
    pub fee_bps: u16,
    pub creator_stake_threshold: u64,
    pub missions_created: u64,
    pub bump: u8,
}

#[derive(Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum MissionStatus {
    Open = 0,
    Filled = 1,
    Closed = 2,
}

#[account]
#[derive(InitSpace)]
pub struct Mission {
    pub id: u64,
    pub creator: Pubkey,
    pub vault: Pubkey,
    pub reward: u64,
    pub slots: u16,
    pub filled: u16,
    pub lat_e6: i32,
    pub lon_e6: i32,
    pub radius_m: u16,
    pub deadline: i64,
    pub requires_seeker: bool,
    pub min_score: u16,
    pub category: u8,
    pub proof_kind: u8,
    pub status: u8,
    pub created_at: i64,
    pub fee_paid: u64,
    #[max_len(48)]
    pub title: String,
    #[max_len(200)]
    pub instructions: String,
    #[max_len(80)]
    pub question: String,
    pub bump: u8,
}

impl Mission {
    pub const TITLE_LEN: usize = 48;
    pub const INSTRUCTIONS_LEN: usize = 200;
    pub const QUESTION_LEN: usize = 80;
}

#[account]
#[derive(InitSpace)]
pub struct WorkerProfile {
    pub wallet: Pubkey,
    pub approved: u32,
    pub rejected: u32,
    pub streak: u16,
    pub last_day: u32,
    pub skr_staked: u64,
    pub seeker_verified: bool,
    pub sgt_mint: Pubkey,
    pub total_earned: u64,
    pub score: u32,
    pub categories: u16,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct CreatorProfile {
    pub wallet: Pubkey,
    pub skr_staked: u64,
    pub missions: u32,
    pub total_funded: u64,
    pub total_paid: u64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct Reservation {
    pub mission: Pubkey,
    pub worker: Pubkey,
    pub expires_at: i64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct Completion {
    pub mission: Pubkey,
    pub worker: Pubkey,
    pub proof_hash: [u8; 32],
    pub confidence: u8,
    pub answer: u8,
    pub amount: u64,
    pub approved_at: i64,
    pub bump: u8,
}

#[account]
#[derive(InitSpace)]
pub struct Device {
    pub sgt_mint: Pubkey,
    pub worker: Pubkey,
    pub bump: u8,
}

// ---------------------------------------------------------------------------
// Events and errors
// ---------------------------------------------------------------------------

#[event]
pub struct MissionCreated {
    pub mission: Pubkey,
    pub id: u64,
    pub creator: Pubkey,
    pub reward: u64,
    pub slots: u16,
}

#[event]
pub struct Reserved {
    pub mission: Pubkey,
    pub worker: Pubkey,
    pub expires_at: i64,
}

#[event]
pub struct Completed {
    pub mission: Pubkey,
    pub worker: Pubkey,
    pub amount: u64,
    pub proof_hash: [u8; 32],
    pub confidence: u8,
    pub answer: u8,
}

#[event]
pub struct Staked {
    pub wallet: Pubkey,
    pub amount: u64,
    pub total: u64,
    pub role: u8,
}

#[error_code]
pub enum LegworkError {
    #[msg("Fee above 20%")]
    FeeTooHigh,
    #[msg("Reward must be positive")]
    InvalidReward,
    #[msg("Slots must be positive")]
    InvalidSlots,
    #[msg("Radius must be between 10 m and 2 km")]
    InvalidRadius,
    #[msg("Text field too long")]
    TextTooLong,
    #[msg("Latitude or longitude out of range")]
    InvalidLocation,
    #[msg("Deadline has passed")]
    DeadlinePassed,
    #[msg("Arithmetic overflow")]
    MathOverflow,
    #[msg("Mission is not open")]
    MissionClosed,
    #[msg("All slots are filled")]
    MissionFull,
    #[msg("Only the verifier can do this")]
    NotVerifier,
    #[msg("Only the creator can do this")]
    NotCreator,
    #[msg("Worker score below the mission minimum")]
    ScoreTooLow,
    #[msg("Mission requires a verified Seeker")]
    SeekerRequired,
    #[msg("This Seeker is already bound to another wallet")]
    DeviceAlreadyBound,
    #[msg("Invalid amount")]
    InvalidAmount,
}
