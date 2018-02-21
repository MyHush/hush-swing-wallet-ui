package org.myhush.gui.environment.file;

import java.io.File;

public class MacPathProvider extends PathProvider {
    @Override
    File getPlatformBlockchainDirectory() {
        return new File(System.getProperty("user.home"), "Library/Application Support/Hush");
    }

    @Override
    File getPlatformSettingsDirectory() {
        return new File(System.getProperty("user.home"), "Library/Application Support/HUSHSwingWalletUI");
    }

    @Override
    public File getZcashParamsDirectory() {
        // @see https://github.com/MyHush/hush/blob/12677875f21c165caf481284ddd45356411c149c/src/util.cpp#L479
        return new File(System.getProperty("user.home"), "Library/Application Support/ZcashParams");
    }
}
