package com.buge.store.platform;

import android.os.ParcelFileDescriptor;

interface IInstallService {
    void destroy();
    boolean install(ParcelFileDescriptor apk, long size, String installerPackageName);
}
