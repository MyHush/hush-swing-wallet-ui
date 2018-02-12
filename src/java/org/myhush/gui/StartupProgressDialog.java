// Copyright (c) 2016 https://github.com/zlatinb
// Copyright (c) 2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

class StartupProgressDialog extends JFrame {
    private static final int POLL_PERIOD_MS = 500;
    private static final int STARTUP_ERROR_CODE = -28;

    private final JLabel progressLabel = new JLabel();
    private final HushCommandLineBridge cliBridge;

    StartupProgressDialog(final HushCommandLineBridge cliBridge) {
        this.cliBridge = cliBridge;

        final URL iconUrl = this.getClass().getClassLoader().getResource("images/hush-logo.png");
        final JLabel imageLabel = new JLabel();
        imageLabel.setIcon(new ImageIcon(iconUrl));
        imageLabel.setBorder(BorderFactory.createEmptyBorder(32, 32, 0, 32));
        final Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        final JPanel southPanel = new JPanel();
        southPanel.setLayout(new BorderLayout());
        southPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 16, 16));
        contentPane.add(imageLabel, BorderLayout.NORTH);
        final JLabel hushWalletLabel = new JLabel(
                "<html><span style=\"font-style:italic;font-weight:bold;font-size:24px\">HUSH Wallet UI</span></html>"
        );
        hushWalletLabel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        contentPane.add(hushWalletLabel, BorderLayout.CENTER);
        contentPane.add(southPanel, BorderLayout.SOUTH);
        final JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        southPanel.add(progressBar, BorderLayout.NORTH);
        progressLabel.setText("Starting...");
        southPanel.add(progressLabel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);

        this.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    }

    public void waitForStartup() throws Exception {
        // special handling of Windows app launch
        if (OSUtil.getOSType() == OSUtil.OS_TYPE.WINDOWS) {
            final ProvingKeyFetcher keyFetcher = new ProvingKeyFetcher();
            keyFetcher.fetchIfMissing(this);
            if ("true".equalsIgnoreCase(System.getProperty("launching.from.appbundle"))) {
                performWinBundleLaunch();
            }
        }

        System.out.println("Splash: checking if hushd is already running...");
        try {
            cliBridge.getDaemonRawRuntimeInfo();
            System.out.println("Splash: hushd already running...");
        } catch (final HushCommandLineBridge.DaemonUnavailableException e) {
            System.out.println("Splash: hushd will be started...");
            final Process daemonProcess = startDaemon();
            scheduleDaemonShutdown(daemonProcess);
        }
    }

    /**
     * This is a _blocking_ call to start the daemon and display the progress via the splash screen
     *
     * @return The daemon's newly created Process
     * @throws IOException
     * @throws InterruptedException
     * @throws DaemonStartupFailureException Upon failing to start the daemon in a timely manner
     */
    private Process startDaemon() throws IOException, InterruptedException, DaemonStartupFailureException {
        final Process daemonProcess = cliBridge.startDaemon();

        final String loadingMessage = "Waiting for daemon to start...";
        setProgressText(loadingMessage);

        int iteration = 1;
        int iterationLimit = 30;
        do {
            Thread.sleep(POLL_PERIOD_MS);
            iteration++;

            final JsonObject daemonInfo;
            try {
                daemonInfo = cliBridge.getDaemonRawRuntimeInfo();
            } catch (final HushCommandLineBridge.DaemonUnavailableException e) {
                setProgressText(loadingMessage + " (" + (iterationLimit - iteration) + ")");

                // wait iterationLimit * POLL_PERIOD_MS intervals before asking user what they want to do
                if (iteration > iterationLimit) {
                    final int dialogResult = JOptionPane.showConfirmDialog(null, "Daemon startup has timed out, do you want to continue waiting?", "Warning", JOptionPane.YES_NO_OPTION);
                    if (dialogResult != JOptionPane.YES_OPTION) {
                        throw new DaemonStartupFailureException("Unable to start hushd daemon");
                    } else {
                        // reset and continue waiting
                        iteration = 1;
                    }
                }
                continue;
            }

            final JsonValue code = daemonInfo.get("code");
            if (code == null || (code.asInt() != STARTUP_ERROR_CODE)) {
                break;
            }
            final String message = daemonInfo.getString("message", "");
            setProgressText(message);
        } while (true);

        return daemonProcess;
    }

    /**
     * Schedule the daemon process for shutdown when this Runtime is finished
     *
     * @param daemonProcess The Process of the running daemon
     */
    private void scheduleDaemonShutdown(final Process daemonProcess) {
        if (daemonProcess == null) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!daemonProcess.isAlive()) {
                System.out.println("Daemon process hushd is not alive");
                return;
            }
            System.out.println("Stopping hushd");

            try {
                cliBridge.stopDaemon();
                final long start = System.currentTimeMillis();

                while (!daemonProcess.waitFor(3000, TimeUnit.MILLISECONDS)) {
                    final long end = System.currentTimeMillis();
                    System.out.println("Waiting for " + ((end - start) / 1000) + " seconds for hushd to exit...");

                    if (end - start > 10 * 1000) {
                        cliBridge.stopDaemon();
                        daemonProcess.destroy();
                    }
                    if (end - start > 60 * 1000) {
                        break;
                    }
                }

                if (daemonProcess.isAlive()) {
                    System.out.println("hushd is still alive although we tried to stop it. Hopefully it will stop later!");
                    // We could choose to attempt to forcibly kill hushd, but if it's in the middle of potentially
                    // termination-unsafe operation, it's best to try to let it finish on it's own
                } else {
                    System.out.println("hushd shut down successfully");
                }
            } catch (final Exception e) {
                System.out.println("Couldn't stop hushd!");
                e.printStackTrace();
            }
        }));
    }

    public void doDispose() {
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            dispose();
        });
    }

    public void setProgressText(final String text) {
        SwingUtilities.invokeLater(() -> progressLabel.setText(text));
    }

    // BRX-TODO: Remove me, why can't I make `hush.conf` the way other platforms do?
    private void performWinBundleLaunch() throws IOException, InterruptedException {
        System.out.println("performing Win Bundle-specific launch");
        final File programFiles = new File(System.getenv("PROGRAMFILES"));
        final File bundlePath = new File(programFiles, "hush/app").getCanonicalFile();

        // run "first-run.bat"
        final File firstRun = new File(bundlePath, "first-run.bat");
        if (firstRun.exists()) {
            System.out.println("running script " + firstRun.getCanonicalPath());
            final Process firstRunProcess = Runtime.getRuntime().exec(firstRun.getCanonicalPath());
            firstRunProcess.waitFor();
        }
    }

    private class DaemonStartupFailureException extends Exception {
        DaemonStartupFailureException(final String message) {
            super(message);
        }
    }
}
