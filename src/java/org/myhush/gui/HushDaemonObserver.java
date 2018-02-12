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
    HushDaemonObserver(final String installDirPath) throws IOException {
        // Detect daemon and client tools installation
        final File installDir = new File(installDirPath);

        if (!installDir.exists() || installDir.isFile()) {
            throw new InstallationDetectionException(
                "The HUSH installation directory " + installDirPath + " does not exist or is not " +
                "a directory or is otherwise inaccessible to the wallet!"
            );
        }

        // BRX-TODO: Same note as in HushCommandLineBridge
        // BRX_TODO: Also, why is this done in two different places?
        File hushd = new File(installDir, OSUtil.getHushd());
        if (!hushd.exists()) {
            hushd = OSUtil.findHushCommand(OSUtil.getHushd());
        }
        File hushcli = new File(installDir, OSUtil.getHushCli());
        if (!hushcli.exists()) {
            hushcli = OSUtil.findHushCommand(OSUtil.getHushCli());
        }

        if ((hushd == null) || !hushd.exists() || (hushcli == null) || !hushcli.exists()) {
            throw new InstallationDetectionException(
                "The HUSH GUI Wallet installation directory " + installDirPath + " needs\n" +
                "to contain the command line utilities hushd and hush-cli. At least one of them is missing!\n" +
                "Please place files HUSHSwingWalletUI.jar, " + OSUtil.getHushCli() + ", " + OSUtil.getHushd() + "\n" +
                "in the same directory."
            );
        }

        System.out.println(
            "Using HUSH utilities: hushd: " + hushd.getCanonicalPath() + ", " + "hush-cli: " + hushcli.getCanonicalPath()
        );
    }

    public synchronized DaemonInfo getDaemonInfo() throws IOException, InterruptedException {
        if (OSUtil.getOSType() == OSUtil.OS_TYPE.WINDOWS) {
            return getDaemonInfoForWindowsOS();
        } else {
            return getDaemonInfoForUNIXLikeOS();
        }
    }

    // BRX-TODO: OS-specific logic needs to be abstracted
    private class NixProcessStatus {
        double cpuPercentage;
        double virtualSizeMB;
        double residentSizeMB;
        String command;
    }

    private NixProcessStatus getProcessStatus(final String processStatusRow) {
        final StringTokenizer stringTokenizer = new StringTokenizer(processStatusRow, " \t", false);
        final NixProcessStatus processStatus = new NixProcessStatus();

        for (int col = 0; col < 11; col++) {
            final String token;
            if (stringTokenizer.hasMoreTokens()) {
                token = stringTokenizer.nextToken();
            } else {
                break;
            }
            switch (col) {
                case 2:
                    try {
                        processStatus.cpuPercentage = Double.valueOf(token);
                    } catch (final NumberFormatException e) { /* TODO: Log or handle exception */ }
                    break;
                case 4:
                    try {
                        processStatus.virtualSizeMB = Double.valueOf(token) / 1000;
                    } catch (final NumberFormatException e) { /* TODO: Log or handle exception */ }
                    break;
                case 5:
                    try {
                        processStatus.residentSizeMB = Double.valueOf(token) / 1000;
                    } catch (final NumberFormatException e) { /* TODO: Log or handle exception */ }
                    break;
                case 10:
                    processStatus.command = token;
                    break;
            }
        }
        return processStatus;
    }

    // So far tested on Mac OS X and Linux - expected to work on other UNIXes as well
    private synchronized DaemonInfo getDaemonInfoForUNIXLikeOS() throws IOException, InterruptedException {
        final DaemonInfo info = new DaemonInfo();
        info.status = DAEMON_STATUS.UNABLE_TO_ASCERTAIN;

        final String psAuxResult = new CommandExecutor(new String[]{ "ps", "auxwww" }).execute();
        // BRX-TODO: Noted `LineNumberReader` used here and elsewhere, but not referring to any line numbers...
        final LineNumberReader lnr = new LineNumberReader(new StringReader(psAuxResult));

        do {
            final String line = lnr.readLine();
            if (line == null) {
                break;
            }
            final NixProcessStatus processStatus = getProcessStatus(line);

            if (processStatus.command.equals("hushd") || processStatus.command.endsWith("/hushd")) {
                info.cpuPercentage = processStatus.cpuPercentage;
                info.residentSizeMB = processStatus.residentSizeMB;
                info.virtualSizeMB = processStatus.virtualSizeMB;
                info.status = DAEMON_STATUS.RUNNING;
                break;
            }
        } while (true);

        if (info.status != DAEMON_STATUS.RUNNING) {
            info.cpuPercentage = 0;
            info.residentSizeMB = 0;
            info.virtualSizeMB = 0;
        }
        return info;
    }


    // BRX-TODO: Switch to `tasklist /fo csv /nh` instead of just `tasklist` for CSV output
    // BRX-TODO: ^ https://stackoverflow.com/a/47987808
    private synchronized DaemonInfo getDaemonInfoForWindowsOS() throws IOException, InterruptedException {
        final DaemonInfo info = new DaemonInfo();
        info.status = DAEMON_STATUS.UNABLE_TO_ASCERTAIN;
        info.cpuPercentage = 0;
        info.virtualSizeMB = 0;

        final String tasklist = new CommandExecutor(new String[]{ "tasklist" }).execute();
        // BRX-TODO: Same comment here re. LineNumberReader as above
        final LineNumberReader lnr = new LineNumberReader(new StringReader(tasklist));

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

    class InstallationDetectionException extends IOException {
        InstallationDetectionException(String message) {
            super(message);
        }
    }
}
