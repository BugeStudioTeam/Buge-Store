package com.buge.store.platform;

interface IInstallService {
    void destroy();
    boolean install(String path, String installerPackageName);
}
