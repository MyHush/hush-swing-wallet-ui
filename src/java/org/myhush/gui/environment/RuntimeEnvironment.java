// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui.environment;

import org.myhush.gui.App;
import org.myhush.gui.CommandExecutor;
import org.myhush.gui.environment.file.*;
import org.myhush.gui.environment.system.DaemonInfoProvider;
import org.myhush.gui.environment.system.NonWindowsDaemonInfoProvider;
import org.myhush.gui.environment.system.WindowsDaemonInfoProvider;
import org.myhush.gui.environment.text.NonWindowsSpecialCharacterProvider;
import org.myhush.gui.environment.text.SpecialCharacterProvider;
import org.myhush.gui.environment.text.WindowsSpecialCharacterProvider;

import java.io.IOException;

public class RuntimeEnvironment {
    private enum OS_TYPE { WINDOWS, MAC, NIX, UNKNOWN }
    private static final OS_TYPE osType;
    static {
        final String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            osType = OS_TYPE.WINDOWS;
        } else if (osName.contains("darwin") || osName.contains("mac os") || osName.contains("macos")) {
            osType = OS_TYPE.MAC;
        } else if (osName.contains("linux") || osName.contains("unix") ||
            osName.contains("aix") ||
            osName.contains("sunos") || osName.contains("solaris") ||
            (osName.contains("free") && osName.contains("bsd")) ||
            (osName.contains("open") || osName.contains("net")) && osName.contains("bsd")) {
            osType = OS_TYPE.NIX;
        } else {
            osType = OS_TYPE.UNKNOWN;
        }
    }

    // This is an unfortunate display of laziness
    public static boolean isWindowsRuntime() {
        return osType == OS_TYPE.WINDOWS;
    }

    private static String getExecutableFileName(final String executableName) {
        if (osType == OS_TYPE.WINDOWS) {
            return executableName + ".exe";
        }
        return executableName;
    }

    public static String getHushDaemonFileName() {
        return getExecutableFileName(App.BINARY_HUSH_DAEMON_BASENAME);
    }

    public static String getHushCliFileName() {
        return getExecutableFileName(App.BINARY_HUSH_CLI_BASENAME);
    }

    public static PathProvider getPathProvider() {
        if (osType == OS_TYPE.WINDOWS) {
            return new WindowsPathProvider();
        } else if (osType == OS_TYPE.MAC) {
            return new MacPathProvider();
        } else if (osType == OS_TYPE.NIX) {
            return new LinuxPathProvider();
        }
        // BRX-NOTE: Maybe this should just default to the LinuxPathProvider and avoid Mr. Unknown
        return new DefaultPathProvider();
    }

    public static SpecialCharacterProvider getSpecialCharacterProvider() {
        if (osType == OS_TYPE.WINDOWS) {
            return new WindowsSpecialCharacterProvider();
        } else {
            return new NonWindowsSpecialCharacterProvider();
        }
    }

    public static DaemonInfoProvider getDaemonInfoProvider() {
        if (osType == OS_TYPE.WINDOWS) {
            return new WindowsDaemonInfoProvider();
        } else {
            return new NonWindowsDaemonInfoProvider();
        }
    }

    public static String getSystemInfo() throws IOException, InterruptedException {
        if (osType == OS_TYPE.WINDOWS) {
            // TODO: More detailed Windows information
            return System.getProperty("os.name");
        } else if (osType == OS_TYPE.MAC) {
            final CommandExecutor uname = new CommandExecutor(new String[]{ "uname", "-sr" });
            return uname.execute() + "; " + System.getProperty("os.name") + " " + System.getProperty("os.version");
        } else {
            return new CommandExecutor(new String[]{ "uname", "-srv" }).execute();
        }
    }
}
