package com.auristube.bridge

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Activity puente invisible: recibe cualquier intent dirigido al paquete
 * "YouTube" (mosaico del launcher, boton del mando, voz, busqueda, tarjetas
 * de la home) y lo reenvia a TizenTube Cobalt.
 */
class BridgeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forward(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        forward(intent)
    }

    private fun forward(incoming: Intent?) {
        try {
            startActivity(buildForwardIntent(incoming))
        } catch (first: ActivityNotFoundException) {
            openTargetHome(incoming)
        } catch (blocked: SecurityException) {
            openTargetHome(incoming)
        } finally {
            finish()
        }
    }

    private fun buildForwardIntent(incoming: Intent?): Intent {
        val action = incoming?.action ?: Intent.ACTION_VIEW
        val data = incoming?.data
        val outgoing = if (data != null) Intent(action, data) else Intent(action)
        outgoing.setPackage(TARGET_PACKAGE)
        copyPayload(incoming, outgoing)
        return outgoing
    }

    private fun openTargetHome(incoming: Intent?) {
        var launcher = packageManager.getLeanbackLaunchIntentForPackage(TARGET_PACKAGE)
        if (launcher == null) {
            launcher = packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
        }
        if (launcher == null) {
            startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }

        copyPayload(incoming, launcher)
        try {
            startActivity(launcher)
        } catch (cannotOpen: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_cannot_open, Toast.LENGTH_LONG).show()
        } catch (blocked: SecurityException) {
            Toast.makeText(this, R.string.error_cannot_open, Toast.LENGTH_LONG).show()
        }
    }

    private fun copyPayload(source: Intent?, target: Intent) {
        if (source == null) {
            return
        }

        source.extras?.let { target.putExtras(it) }
        source.clipData?.let { target.clipData = it }
        source.type?.let { type ->
            target.data?.let { data -> target.setDataAndType(data, type) }
        }

        target.addFlags(
            source.flags and (
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
        )
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        private const val TARGET_PACKAGE = "io.gh.reisxd.tizentube.cobalt"
    }
}
