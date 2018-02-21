package org.myhush.gui.environment.file;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public abstract class PathProvider {
    //  This is currently the same for every OS (may need further optimization for edge cases)
    private File determineProgramDirectory() {
        final String path = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        try {
            final String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
            return new File(decodedPath).getParentFile();
        } catch (final UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public final File getProgramDirectory() {
        final String programDirectory = System.getProperty("program.directory");
        if (programDirectory != null) {
            return new File(programDirectory);
        }
        return determineProgramDirectory();
    }

    abstract File getPlatformBlockchainDirectory();

    public final File getBlockchainDirectory() {
        final String userDataDirectory = System.getProperty("data.directory");
        if (userDataDirectory != null) {
            return new File(userDataDirectory);
        }
        return getPlatformBlockchainDirectory();
    }

    abstract File getPlatformSettingsDirectory();

    public final File getSettingsDirectory() {
        final String userSettingsDirectory = System.getProperty("settings.directory");
        final File settingsDirectory;
        if (userSettingsDirectory != null) {
            settingsDirectory = new File(userSettingsDirectory);
        }
        else {
            settingsDirectory = getPlatformSettingsDirectory();
        }
        // BRX-NOTE: This probably shouldn't be here, but for legacy compatibility it's kept (should be refactored)
        if (!settingsDirectory.exists()) {
            if (!settingsDirectory.mkdirs()) {
                try {
                    System.out.println("WARNING: Could not create settings directory: " + settingsDirectory.getCanonicalPath());
                } catch (final IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return settingsDirectory;
    }

    public abstract File getZcashParamsDirectory();
}
