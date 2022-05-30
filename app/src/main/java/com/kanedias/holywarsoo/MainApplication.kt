package com.kanedias.holywarsoo

import android.app.Application
import android.content.Context
import com.kanedias.holywarsoo.service.Config
import com.kanedias.holywarsoo.service.Database
import org.acra.data.StringFormat
import com.kanedias.holywarsoo.service.Network
import com.kanedias.holywarsoo.service.SmiliesCache
import org.acra.config.dialog
import org.acra.config.mailSender
import org.acra.ktx.initAcra


/**
 * Main application class.
 * Place to initialize all data prior to launching activities.
 *
 * @author Kanedias
 *
 * Created on 2019-12-27
 */
class MainApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        initAcra {
            alsoReportToAndroidFramework = true
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON

            dialog {
                resIcon = R.drawable.ic_launcher_round
                withText(getString(R.string.app_crashed))
                withCommentPrompt(getString(R.string.leave_crash_comment))
                withResTheme(R.style.FireTheme)
            }

            mailSender {
                mailTo = "kanedias@gmx.net"
                reportFileName = "crash-report.json"
                withSubject(getString(R.string.app_crash_report))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        Config.init(this)
        Network.init(this)
        Database.init(this)
        SmiliesCache.init(this)
    }
}
