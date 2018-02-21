package org.myhush.gui.environment.file;

import java.io.File;

public class WindowsPathProvider extends PathProvider {
    @Override
    File getPlatformBlockchainDirectory() {
        return new File(System.getenv("APPDATA"), "Hush");
    }

    @Override
    File getPlatformSettingsDirectory() {
        return new File(System.getenv("LOCALAPPDATA"), "HUSHSwingWalletUI");
    }

    @Override
    public File getZcashParamsDirectory() {
        // @see https://github.com/MyHush/hush/blob/12677875f21c165caf481284ddd45356411c149c/src/util.cpp#L479
        return new File(System.getenv("APPDATA"), "ZcashParams");
    }
}
