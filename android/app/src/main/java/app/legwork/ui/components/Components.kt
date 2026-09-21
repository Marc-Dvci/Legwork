package app.legwork.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.legwork.core.Categories
import app.legwork.core.Format
import app.legwork.core.Geo
import app.legwork.data.Check
import app.legwork.data.Mission
import app.legwork.ui.theme.Legwork

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
fun RewardTag(micro: Long, large: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Legwork.Money)
            .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 8.dp else 5.dp)
    ) {
        Text(
            Format.usdcShort(micro),
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = Color.White, fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
fun SeekerBadge(small: Boolean = false) {
    Pill(
        if (small) "Seeker" else "Seeker verified", Legwork.SeekerSoft, Legwork.Seeker,
        icon = Icons.Filled.Verified,
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
        .background(Legwork.Surface)
        .border(1.dp, Legwork.Line, MaterialTheme.shapes.large)
    Box(if (onClick != null) m.clickable(onClick = onClick) else m) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (trailing != null) Text(
            trailing, style = MaterialTheme.typography.labelLarge, color = Legwork.Accent,
            modifier = Modifier.clickable(enabled = onTrailing != null) { onTrailing?.invoke() },
        )
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false, color: Color = Legwork.Accent) {
    Button(
        onClick = onClick, enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White, disabledContainerColor = Legwork.Line, disabledContentColor = Legwork.Muted),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Legwork.SurfaceAlt, contentColor = Legwork.Ink, disabledContainerColor = Legwork.SurfaceAlt, disabledContentColor = Legwork.Muted),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun ProofKindIcon(kind: Int, tint: Color = Legwork.Muted) {
    val icon = when (kind) { 1 -> Icons.Outlined.QrCode; else -> Icons.Outlined.CameraAlt }
    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
}

fun proofKindLabel(kind: Int) = when (kind) {
    1 -> "QR scan + location"
    else -> "Photo + location"
}

@Composable
fun CategoryDot(category: Int, size: Int = 40) {
    val cat = Categories.byId(category)
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Legwork.SurfaceAlt),
        contentAlignment = Alignment.Center,
    ) { Text(cat.emoji, style = MaterialTheme.typography.titleLarge) }
}

@Composable
fun MissionCard(m: Mission, distanceM: Double?, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryDot(m.category)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = Legwork.Muted, modifier = Modifier.size(14.dp))
                        Text(
                            " ${Geo.formatDistance(distanceM)} · ${Geo.walkMinutes(distanceM)} min walk",
                            style = MaterialTheme.typography.bodySmall, color = Legwork.Muted,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                RewardTag(m.reward)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProofKindIcon(m.proofKind)
                Text(proofKindLabel(m.proofKind), style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
                Spacer(Modifier.weight(1f))
                if (m.requiresSeeker) SeekerBadge(small = true)
                if (m.completedByMe) Pill("Done", Legwork.MoneySoft, Legwork.Money, icon = Icons.Filled.Check)
                else Pill("${m.remaining} left", Legwork.SurfaceAlt, Legwork.Muted)
            }
        }
    }
}

@Composable
fun CheckRow(c: Check) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (c.pass) Legwork.Money else Legwork.Danger),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (c.pass) Icons.Filled.Check else Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(c.name, style = MaterialTheme.typography.titleSmall)
            if (c.detail.isNotBlank()) Text(c.detail, style = MaterialTheme.typography.bodySmall, color = Legwork.Muted)
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Legwork.Ink) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = Legwork.Surface, border = androidx.compose.foundation.BorderStroke(1.dp, Legwork.Line)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Legwork.Muted)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🥾", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Legwork.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryButton(action, onAction)
        }
    }
}
