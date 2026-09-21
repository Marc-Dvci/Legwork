package app.legwork

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import app.legwork.ui.LegworkNav
import app.legwork.ui.theme.LegworkTheme
import app.legwork.vm.AppViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private lateinit var sender: ActivityResultSender
    private val pendingMission = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sender = ActivityResultSender(this)
        pendingMission.value = intent?.getStringExtra("mission")
        setContent {
            LegworkTheme {
                LegworkNav(vm = vm, sender = sender, pendingMission = pendingMission)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingMission.value = intent.getStringExtra("mission")
    }
}
