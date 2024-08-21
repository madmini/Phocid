package org.sunsetware.phocid

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.File
import java.lang.ref.WeakReference
import kotlin.system.exitProcess
import org.sunsetware.phocid.utils.icuFormat

class MainApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        stringSource = WeakReference(base)

        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            Log.e("Phocid", "Uncaught exception", ex)
            val file = File(getExternalFilesDir(null), "crash.txt")

            file.bufferedWriter().use { writer ->
                writer.write(BuildConfig.VERSION_NAME)
                writer.write("\n\n")
                writer.write(ex.stackTraceToString())
                writer.write("\n\n")

                try {
                    Runtime.getRuntime().exec("logcat -d").inputStream.bufferedReader().use { reader
                        ->
                        while (true) {
                            val line = reader.readLine()
                            if (line == null) break
                            writer.write(line)
                            writer.write("\n")
                        }
                    }
                } catch (ex: Exception) {
                    writer.write(/* NON-NLS */ "An exception occurred reading logcat:\n")
                    writer.write(ex.stackTraceToString())
                }
            }

            Toast.makeText(
                    base,
                    Strings[R.string.toast_crash_saved_to].icuFormat(file.path),
                    Toast.LENGTH_LONG,
                )
                .show()

            exitProcess(1)
        }
    }
}
