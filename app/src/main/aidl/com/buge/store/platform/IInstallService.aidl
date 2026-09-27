package com.buge.store.platform;

import com.buge.store.platform.IInstallCallback;

interface IInstallService {
    void destroy();
    boolean install(in ParcelFileDescriptor apk, long size, String installerPackageName, IInstallCallback callback);
}
