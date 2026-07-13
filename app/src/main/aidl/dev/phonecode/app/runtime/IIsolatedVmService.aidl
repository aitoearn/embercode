package dev.phonecode.app.runtime;

import android.os.ParcelFileDescriptor;

interface IIsolatedVmService {
    void start(
        in ParcelFileDescriptor kernel,
        in ParcelFileDescriptor initramfs,
        in ParcelFileDescriptor console,
        in ParcelFileDescriptor control
    );
    void stop();
}
