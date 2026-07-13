package dev.phonecode.app.runtime;

import android.os.ParcelFileDescriptor;

interface IIsolationProbe {
    int processUid();
    int appUid();
    boolean isolated();
    boolean canRead(String path);
    String read(in ParcelFileDescriptor descriptor);
}
