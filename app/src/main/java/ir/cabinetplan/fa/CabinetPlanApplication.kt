package ir.cabinetplan.fa

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import dagger.hilt.android.HiltAndroidApp
import java.util.Locale

@HiltAndroidApp
class CabinetPlanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale.forLanguageTag("fa"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.toLanguageTags() != "fa") {
                manager.applicationLocales = LocaleList.forLanguageTags("fa")
            }
        }
    }
}
