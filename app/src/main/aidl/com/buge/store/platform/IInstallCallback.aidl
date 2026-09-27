package com.buge.store.platform;

oneway interface IInstallCallback {
    void onStep(String step, String detail);
}
