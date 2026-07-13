package dev.phonecode.app.runtime

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.FileInputStream

class IsolationProbeService : Service() {
    private val binder = object : IIsolationProbe.Stub() {
        override fun processUid(): Int = Process.myUid()

        override fun appUid(): Int = applicationInfo.uid

        override fun isolated(): Boolean =
            if (Build.VERSION.SDK_INT >= 28) Process.isIsolated() else Process.myUid() != applicationInfo.uid

        override fun canRead(path: String): Boolean = runCatching {
            FileInputStream(path).use { it.read() }
            true
        }.getOrDefault(false)

        override fun read(descriptor: ParcelFileDescriptor): String =
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    override fun onBind(intent: Intent): IBinder = binder
}
