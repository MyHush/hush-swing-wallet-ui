// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.StringTokenizer;

/**
 * Observes the daemon - running etc.
 */
public class HushDaemonObserver {
    HushDaemonObserver(String installDir)
            throws IOException {
        // Detect daemon and client tools installation
        File dir = new File(installDir);

        if (!dir.exists() || dir.isFile()) {
            throw new InstallationDetectionException(
                    "The HUSH installation directory " + installDir + " does not exist or is not " +
                            "a directory or is otherwise inaccessible to the wallet!");
        }

        File hushd = new File(dir, OSUtil.getHushd());
        File hushcli = new File(dir, OSUtil.getHushCli());

        if ((!hushd.exists()) || (!hushcli.exists())) {
            hushd = OSUtil.findHushCommand(OSUtil.getHushd());
            hushcli = OSUtil.findHushCommand(OSUtil.getHushCli());
        }

        System.out.println("Using HUSH utilities: " +
                                   "hushd: " + ((hushd != null) ? hushd.getCanonicalPath() : "<MISSING>") + ", " +
                                   "hush-cli: " + ((hushcli != null) ? hushcli.getCanonicalPath() : "<MISSING>"));

        if ((hushd == null) || (hushcli == null) || (!hushd.exists()) || (!hushcli.exists())) {
            throw new InstallationDetectionException(
                    "The HUSH GUI Wallet installation directory " + installDir + " needs\nto contain " +
                            "the command line utilities hushd and hush-cli. At least one of them is missing! \n" +
                            "Please place files HUSHSwingWalletUI.jar, " + OSUtil.getHushCli() + ", " +
                            OSUtil.getHushd() + " in the same directory.");
        }
    }

    public synchronized DaemonInfo getDaemonInfo()
            throws IOException, InterruptedException {
        OSUtil.OS_TYPE os = OSUtil.getOSType();

        if (os == OSUtil.OS_TYPE.WINDOWS) {
            return getDaemonInfoForWindowsOS();
        } else {
            return getDaemonInfoForUNIXLikeOS();
        }
    }

    // So far tested on Mac OS X and Linux - expected to work on other UNIXes as well
    private synchronized DaemonInfo getDaemonInfoForUNIXLikeOS()
            throws IOException, InterruptedException {
        DaemonInfo info = new DaemonInfo();
        info.status = DAEMON_STATUS.UNABLE_TO_ASCERTAIN;

        CommandExecutor exec = new CommandExecutor(new String[]{ "ps", "auxwww" });
        LineNumberReader lnr = new LineNumberReader(new StringReader(exec.execute()));

        String line;
        while ((line = lnr.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, " \t", false);
            boolean foundHush = false;
            label:
            for (int i = 0; i < 11; i++) {
                final String token;
                if (st.hasMoreTokens()) {
                    token = st.nextToken();
                } else {
                    break;
                }

                switch (i) {
                    case 2:
                        try {
                            info.cpuPercentage = Double.valueOf(token);
                        } catch (NumberFormatException nfe) { /* TODO: Log or handle exception */ }
                        break;
                    case 4:
                        try {
                            info.virtualSizeMB = Double.valueOf(token) / 1000;
                        } catch (NumberFormatException nfe) { /* TODO: Log or handle exception */ }
                        break;
                    case 5:
                        try {
                            info.residentSizeMB = Double.valueOf(token) / 1000;
                        } catch (NumberFormatException nfe) { /* TODO: Log or handle exception */ }
                        break;
                    case 10:
                        if ((token.equals("hushd")) || (token.endsWith("/hushd"))) {
                            info.status = DAEMON_STATUS.RUNNING;
                            foundHush = true;
                            break label;
                        }
                        break;
                }
            }

            if (foundHush) {
                break;
            }
        }

        if (info.status != DAEMON_STATUS.RUNNING) {
            info.cpuPercentage = 0;
            info.residentSizeMB = 0;
            info.virtualSizeMB = 0;
        }

        return info;
    }

    private synchronized DaemonInfo getDaemonInfoForWindowsOS()
            throws IOException, InterruptedException {
        DaemonInfo info = new DaemonInfo();
        info.status = DAEMON_STATUS.UNABLE_TO_ASCERTAIN;
        info.cpuPercentage = 0;
        info.virtualSizeMB = 0;

        CommandExecutor exec = new CommandExecutor(new String[]{ "tasklist" });
        LineNumberReader lnr = new LineNumberReader(new StringReader(exec.execute()));

        String line;
        while ((line = lnr.readLine()) != null) {
            final StringTokenizer stringTokenizer = new StringTokenizer(line, " \t", false);
            boolean foundHush = false;
            final StringBuilder size = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (!stringTokenizer.hasMoreTokens()) {
                    break;
                }
                final String token = stringTokenizer.nextToken().replaceAll("^\"|\"$", "");

                if (i == 0) {
                    if (token.equals("hushd.exe") || token.equals("hushd")) {
                        info.status = DAEMON_STATUS.RUNNING;
                        foundHush = true;
                        //System.out.println("Hushd process data is: " + line);
                    }
                } else if ((i >= 4) && foundHush) {
                    try {
                        size.append(token.replaceAll("[^0-9]", ""));
                        if (size.toString().endsWith("K")) {
                            size.setLength(size.length() - 1);
                        }
                    } catch (NumberFormatException nfe) { /* TODO: Log or handle exception */ }
                }
            } // End parsing row

            if (foundHush) {
                try {
                    info.residentSizeMB = Double.valueOf(size.toString()) / 1000;
                } catch (NumberFormatException nfe) {
                    info.residentSizeMB = 0;
                    System.out.println("Error: could not find the numeric memory size of hushd: " + size);
                }
                break;
            }
        }

        if (info.status != DAEMON_STATUS.RUNNING) {
            info.cpuPercentage = 0;
            info.residentSizeMB = 0;
            info.virtualSizeMB = 0;
        }

        return info;
    }


    public enum DAEMON_STATUS {
        RUNNING,
        NOT_RUNNING,
        UNABLE_TO_ASCERTAIN
    }

    public static class DaemonInfo {
        public DAEMON_STATUS status;
        public double residentSizeMB;
        public double virtualSizeMB;
        public double cpuPercentage;
    }

    public static class InstallationDetectionException
            extends IOException {
        InstallationDetectionException(String message) {
            super(message);
        }
    }
}
