package org.myhush.gui.environment.file;

import java.io.File;
import java.nio.file.Paths;

public class DefaultPathProvider extends PathProvider {
    @Override
    File getPlatformBlockchainDirectory() {
        return new File(Paths.get(".").toAbsolutePath().getParent().toString(), ".hush");
    }

    @Override
    File getPlatformSettingsDirectory() {
        return new File(Paths.get(".").toAbsolutePath().getParent().toString(), ".HUSHSwingWalletUI");
    }

    @Override
    public File getZcashParamsDirectory() {
        // @see https://github.com/MyHush/hush/blob/12677875f21c165caf481284ddd45356411c149c/src/util.cpp#L479
        return new File(System.getProperty("user.home"), ".zcash-params");
    }
}
