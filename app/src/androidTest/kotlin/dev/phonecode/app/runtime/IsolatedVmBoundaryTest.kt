package dev.phonecode.app.runtime

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class IsolatedVmBoundaryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Suppress("DEPRECATION")
    @Test
    fun productionManifestIsPrivateAndIsolated() {
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, IsolatedQemuService::class.java),
            0,
        )

        assertFalse(info.exported)
        assertTrue(info.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0)
    }

    @Test
    fun isolatedUidCannotReadPrivateSentinelButCanUseDelegatedFd() {
        val sentinel = File(context.filesDir, "isolation-sentinel").apply {
            writeText("PHONECODE_FD_OK")
        }
        val connected = CountDownLatch(1)
        val probe = AtomicReference<IIsolationProbe>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                probe.set(IIsolationProbe.Stub.asInterface(service))
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }

        assertTrue(
            context.bindService(
                Intent(context, IsolationProbeService::class.java),
                connection,
                Service.BIND_AUTO_CREATE,
            ),
        )
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val isolated = probe.get()
            assertNotEquals(isolated.appUid(), isolated.processUid())
            assertFalse(isolated.canRead(sentinel.absolutePath))
            if (Build.VERSION.SDK_INT >= 28) assertTrue(isolated.isolated())
            ParcelFileDescriptor.open(sentinel, ParcelFileDescriptor.MODE_READ_ONLY).use {
                assertTrue(isolated.read(it) == "PHONECODE_FD_OK")
            }
        } finally {
            context.unbindService(connection)
            sentinel.delete()
        }
    }
}
