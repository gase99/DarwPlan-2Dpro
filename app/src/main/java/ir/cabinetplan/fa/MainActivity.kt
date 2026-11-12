package ir.cabinetplan.fa

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dagger.hilt.android.AndroidEntryPoint
import ir.cabinetplan.fa.ui.canvas.CanvasScreen
import ir.cabinetplan.fa.ui.canvas.CanvasViewModel
import ir.cabinetplan.fa.ui.theme.CabinetPlanTheme
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: CanvasViewModel by viewModels()

    // Persian is the default even on phones whose system language is not Persian.
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale.forLanguageTag("fa")
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CabinetPlanTheme {
                    CanvasScreen(viewModel)
                }
            }
        }
    }
}
