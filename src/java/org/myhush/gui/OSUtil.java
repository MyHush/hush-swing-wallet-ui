// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

// BRX-TODO: This needs to be abstracted up an include a lot more platform-specific logic

/**
 * Utilities - may be OS dependent
 */
public class OSUtil {

    private static boolean isUnixLike(final OS_TYPE os) {
        switch (os) {
            case LINUX:
            case MAC_OS:
            case FREE_BSD:
            case OTHER_BSD:
            case SOLARIS:
            case AIX:
            case OTHER_UNIX:
                return true;
        }
        return false;
    }

    public static boolean isHardUnix(final OS_TYPE os) {
        switch (os) {
            case FREE_BSD:
            case OTHER_BSD:
            case SOLARIS:
            case AIX:
            case OTHER_UNIX:
                return true;
        }
        return false;
    }

    // BRX-TODO: This is called _a lot_, cache result?
    public static OS_TYPE getOSType() {
        final String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (name.contains("linux")) {
            return OS_TYPE.LINUX;
        } else if (name.contains("windows")) {
            return OS_TYPE.WINDOWS;
        } else if (name.contains("sunos") || name.contains("solaris")) {
            return OS_TYPE.SOLARIS;
        } else if (name.contains("darwin") || name.contains("mac os") || name.contains("macos")) {
            return OS_TYPE.MAC_OS;
        } else if (name.contains("free") && name.contains("bsd")) {
            return OS_TYPE.FREE_BSD;
        } else if ((name.contains("open") || name.contains("net")) && name.contains("bsd")) {
            return OS_TYPE.OTHER_BSD;
        } else if (name.contains("aix")) {
            return OS_TYPE.AIX;
        } else if (name.contains("unix")) {
            return OS_TYPE.OTHER_UNIX;
        } else {
            return OS_TYPE.OTHER_OS;
        }
    }

    // Returns the name of the hushd server - may vary depending on the OS.
    public static String getHushd() {
        return "hushd" + ((getOSType() == OS_TYPE.WINDOWS) ? ".exe" : "");
    }

    // Returns the name of the hush-cli tool - may vary depending on the OS.
    public static String getHushCli() {
        return "hush-cli" + ((getOSType() == OS_TYPE.WINDOWS) ? ".exe" : "");
    }

    // Returns the directory that the wallet program was started from
    public static String getProgramDirectory() throws IOException {
        // TODO: this way of finding the dir is JAR name dependent - tricky, may not work
        // if program is repackaged as different JAR!
        final String JAR_NAME = "HUSHSwingWalletUI.jar";
        final String classPath = System.getProperty("java.class.path");
        if ((classPath != null) && !classPath.contains(File.pathSeparator) && classPath.endsWith(JAR_NAME)) {
            final File pd = new File(classPath.substring(0, classPath.length() - JAR_NAME.length()));

            if (pd.exists() && pd.isDirectory()) {
                return pd.getCanonicalPath();
            }
        }

        // Current dir of the running JVM (expected)
        final String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            final File ud = new File(userDir);

            if (ud.exists() && ud.isDirectory()) {
                return ud.getCanonicalPath();
            }
        }

        // TODO: tests and more options
        return new File(".").getCanonicalPath();
    }

    public static File getUserHomeDirectory() {
        return new File(System.getProperty("user.home"));
    }

    public static String getBlockchainDirectory() throws IOException {
        switch (getOSType()) {
            case MAC_OS:
                return new File(System.getProperty("user.home") + "/Library/Application Support/Hush").getCanonicalPath();
            case WINDOWS:
                return new File(System.getenv("APPDATA") + "\\Hush").getCanonicalPath();
            default:
                return new File(System.getProperty("user.home") + "/.hush").getCanonicalPath();
        }
    }

    // Directory with program settings to store
    public static String getSettingsDirectory() throws IOException {
        final File userHome = new File(System.getProperty("user.home"));
        final File settingsDir;

        switch (getOSType()) {
            case MAC_OS:
                settingsDir = new File(userHome, "Library/Application Support/HUSHSwingWalletUI");
                break;
            case WINDOWS:
                settingsDir = new File(System.getenv("LOCALAPPDATA") + "\\HUSHSwingWalletUI");
                break;
            default:
                settingsDir = new File(userHome.getCanonicalPath() + File.separator + ".HUSHSwingWalletUI");
                break;
        }

        if (!settingsDir.exists()) {
            if (!settingsDir.mkdirs()) {
                System.out.println("WARNING: Could not create settings directory: " + settingsDir.getCanonicalPath());
            }
        }
        return settingsDir.getCanonicalPath();
    }

    public static String getSystemInfo() throws IOException, InterruptedException {
        switch (getOSType()) {
            case MAC_OS:
                final CommandExecutor uname = new CommandExecutor(new String[]{ "uname", "-sr" });
                return uname.execute() + "; " + System.getProperty("os.name") + " " + System.getProperty("os.version");
            case WINDOWS:
                // TODO: More detailed Windows information
                return System.getProperty("os.name");
            default:
                return new CommandExecutor(new String[]{ "uname", "-srv" }).execute();
        }
    }

    // Can be used to find hushd/hush-cli if it is not found in the same place as the wallet JAR
    // Null if not found
    // BRX-TODO: This method is a nightmare, revisit ASAP
    public static File findHushCommand(String command) throws IOException {
        File f;

        // Try with system property hush.location.dir - may be specified by caller
        String HushLocationDir = System.getProperty("hush.location.dir");
        if ((HushLocationDir != null) && (HushLocationDir.trim().length() > 0)) {
            f = new File(HushLocationDir + File.separator + command);
            if (f.exists() && f.isFile()) {
                return f.getCanonicalFile();
            }
        }

        OS_TYPE os = getOSType();

        if (isUnixLike(os)) {
            // The following search directories apply to UNIX-like systems only
            final String dirs[] = new String[]
                                          {
                                                  // TODO: HUSH directories!
                                                  "/usr/bin/", // Typical Ubuntu
                                                  "/bin/",
                                                  "/usr/local/bin/",
                                                  "/usr/local/hush/bin/",
                                                  "/usr/lib/hush/bin/",
                                                  "/opt/local/bin/",
                                                  "/opt/local/hush/bin/",
                                                  "/opt/hush/bin/"
                                          };

            for (String d : dirs) {
                f = new File(d + command);
                if (f.exists()) {
                    return f;
                }
            }

        } else if (os == OS_TYPE.WINDOWS) {
            // A probable Windows directory is a Hush dir in Program Files
            String programFiles = System.getenv("PROGRAMFILES");
            if ((programFiles != null) && (!programFiles.isEmpty())) {
                File pf = new File(programFiles);
                if (pf.exists() && pf.isDirectory()) {
                    File ZDir = new File(pf, "Hush");
                    if (ZDir.exists() && ZDir.isDirectory()) {
                        File cf = new File(ZDir, command);
                        if (cf.exists() && cf.isFile()) {
                            return cf;
                        }
                    }
                }
            }
        }

        // Try in the current directory
        f = new File("." + File.separator + command);
        if (f.exists() && f.isFile()) {
            return f.getCanonicalFile();
        }


        // TODO: Try to find it with which/PATH

        return null;
    }

    public enum OS_TYPE {
        LINUX, WINDOWS, MAC_OS, FREE_BSD, OTHER_BSD, SOLARIS, AIX, OTHER_UNIX, OTHER_OS
    }
}
