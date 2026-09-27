package com.buge.store.platform;

interface IInstallService {
    void destroy();
    boolean install(in ParcelFileDescriptor apk, long size, String installerPackageName, IInstallCallback callback);
}
