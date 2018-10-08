// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import org.myhush.gui.environment.RuntimeEnvironment;
import org.myhush.gui.environment.file.PathProvider;
import org.myhush.gui.environment.system.DaemonInfo;
import org.myhush.gui.environment.system.DaemonInfoProvider;
import org.myhush.gui.environment.system.DaemonState;
import org.myhush.gui.environment.text.SpecialCharacterProvider;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

public class App {
    public static final String APP_VERSION_SHORT = "0.72.1b";
    public static final String APP_VERSION_LONG  = "0.72.1 (beta)";
    public static final String APP_JAR_FILENAME  = "HUSHSwingWalletUI.jar";

    public static final PathProvider PATH_PROVIDER = RuntimeEnvironment.getPathProvider();
    public static final SpecialCharacterProvider SPECIAL_CHARACTER_PROVIDER = RuntimeEnvironment.getSpecialCharacterProvider();

    public static final String BINARY_HUSH_DAEMON_BASENAME = "hushd";
    public static final String BINARY_HUSH_CLI_BASENAME = "hush-cli";

    public static void main(final String argv[]) throws IOException {
        try {
            redirectLoggingToFile();
            createHushConfigFileIfNone();
            createAddressBookIfNone();

            System.out.println("Starting HUSH Swing Wallet ...");
            System.out.println("OS: " + System.getProperty("os.name"));
            System.out.println("Current directory: " + new File(".").getCanonicalPath());
            System.out.println("Class path: " + System.getProperty("java.class.path"));
            System.out.println("Environment PATH: " + System.getenv("PATH"));

            // Attempt to adopt the native look and feel of the platform
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            // If hushd is currently not running, do a startup of the daemon as a child process
            // It may be started but not ready - then also show dialog
            final DaemonInfoProvider daemonInfoProvider = RuntimeEnvironment.getDaemonInfoProvider();
            final DaemonInfo hushdInfo = daemonInfoProvider.getDaemonInfo();

            final HushCommandLineBridge commandLineBridge = new HushCommandLineBridge(PATH_PROVIDER.getProgramDirectory());
            final StartupProgressDialog startupBar;
            {
                boolean daemonStartInProgress = false;
                try {
                    if (hushdInfo.status == DaemonState.RUNNING) {
                        final HushCommandLineBridge.NetworkAndBlockchainInfo info = commandLineBridge.getNetworkAndBlockchainInfo();
                        // If more than 20 minutes behind in the blockchain - startup in progress
                        if ((System.currentTimeMillis() - info.lastBlockDate.getTime()) > (20 * 60 * 1000)) {
                            System.out.println("Current blockchain synchronization date is" + new Date(info.lastBlockDate.getTime()));
                            daemonStartInProgress = true;
                        }
                    }
                } catch (final HushCommandLineBridge.WalletCallException wce) {
                    // If we're started, but not ready
                    if ((wce.getMessage().contains("{\"code\":-28")) || (wce.getMessage().contains("error code: -28"))) {
                        System.out.println("`hushd` is currently starting...");
                        daemonStartInProgress = true;
                    }
                }

                if ((hushdInfo.status != DaemonState.RUNNING) || daemonStartInProgress) {
                    System.out.println("`hushd` is not running at the moment or has not started/synchronized 100% - showing splash...");
                    startupBar = new StartupProgressDialog(commandLineBridge);
                    startupBar.setVisible(true);
                    startupBar.waitForStartup();
                } else {
                    startupBar = null;
                }
            }

            // Launch the primary GUI
            new HushWalletFrame(
                    startupBar,
                    daemonInfoProvider,
                    commandLineBridge
            ).setVisible(true);
        } catch (final HushCommandLineBridge.WalletCallException wce) {
            wce.printStackTrace();

            if (wce.getMessage().contains("{\"code\":-28,\"message\"") || wce.getMessage().contains("error code: -28")) {
                JOptionPane.showMessageDialog(
                        null,
                        "It appears that `hushd` has been started but is not ready to accept wallet\n" +
                                "connections. It is still loading the wallet and blockchain. Please try to\n" +
                                "start the GUI wallet later...",
                        "Wallet communication error",
                        JOptionPane.ERROR_MESSAGE
                                             );
            } else {
                JOptionPane.showMessageDialog(
                        null,
                        "There was a problem communicating with the HUSH daemon/wallet. \n" +
                                "Please ensure that the HUSH server `hushd` is started (e.g. via \n" +
                                "command  \"hushd --daemon\"). Error message is: \n" + wce.getMessage() +
                                "See the console output for more detailed error information!",
                        "Wallet communication error",
                        JOptionPane.ERROR_MESSAGE
                                             );
            }
            System.exit(2);
        } catch (final Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "A general unexpected critical error has occurred: \n" + e.getMessage() + "\n" +
                            "See the console output for more detailed error information!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                                         );
            System.exit(3);
        }
    }

    private static void redirectLoggingToFile() throws IOException {
        // Initialize log to a file
        final String settingsDirPath = PATH_PROVIDER.getSettingsDirectory().getCanonicalPath();
        final Date today = new Date();
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        final String logFilePath =
                settingsDirPath + File.separator +
                        "HUSHWallet_" + calendar.get(Calendar.YEAR) + "_" + (calendar.get(Calendar.MONTH) + 1) +
                        "_debug.log";
        final PrintStream fileOut = new PrintStream(new FileOutputStream(logFilePath, true));

        fileOut.println("=================================================================================");
        fileOut.println("= New log started at: " + today.toString());
        fileOut.println("=================================================================================");
        fileOut.println("");

        // Write to console to let potential console users know where the output is going
        System.out.println("HUSH Swing Wallet: Redirecting console output to:\n" + logFilePath);

        System.setOut(fileOut);
        System.setErr(fileOut);
    }

    private static void createHushConfigFileIfNone() throws IOException {
        final File blockchainDirectory = PATH_PROVIDER.getBlockchainDirectory();
        if (!blockchainDirectory.exists()) {
            if (!blockchainDirectory.mkdirs()) {
                System.out.println("ERROR: Could not create data directory: " + blockchainDirectory.getCanonicalPath());
                throw new IOException("Could not create data directory: " + blockchainDirectory.getCanonicalPath());
            }
        }
        final File hushConfigFile = new File(blockchainDirectory, Constants.HUSH_CONFIG_FILENAME);
        if (!hushConfigFile.exists()) {
            final String userMessage = String.format("The HUSH daemon configuration file at \"%s\" does not exist. It will be created with default settings", hushConfigFile.getCanonicalPath());
            System.out.println(userMessage);
            JOptionPane.showMessageDialog(null, userMessage);
            final Random random = new Random(System.currentTimeMillis());
            final PrintStream configOut = new PrintStream(new FileOutputStream(hushConfigFile));
            configOut.println("#############################################################################");
            configOut.println("#                         HUSH configuration file                           #");
            configOut.println("#############################################################################");
            configOut.println("# This file has been automatically generated by the HUSH GUI wallet with    #");
            configOut.println("# default settings. It may be further cutsomized by hand only.              #");
            configOut.println("#############################################################################");
            configOut.println("# Creation date: " + new Date().toString());
            configOut.println("#############################################################################");
            configOut.println("");
            configOut.println("# The rpcuser/rpcpassword are used for the local call to `hushd`");
            configOut.println("rpcuser=user" + (random.nextInt() & Integer.MAX_VALUE));
            configOut.println("rpcpassword=pass" + (random.nextInt() & Integer.MAX_VALUE));
            configOut.println("");
            configOut.println("addnode=explorer.myhush.org");
            configOut.println("addnode=stilgar.leto.net");
            configOut.println("addnode=zdash.suprnova.cc");
            configOut.println("addnode=dnsseed.myhush.org");
            configOut.println("addnode=dnsseed2.myhush.org");
            configOut.println("addnode=dnsseed.bleuzero.com");
            configOut.println("addnode=dnsseed.hush.quebec");
            configOut.println("");
            configOut.println("daemon=1");
            configOut.println("showmetrics=0");
            configOut.println("gen=0");
            configOut.close();
        }
    }

    private static void createAddressBookIfNone() throws IOException {
        final File settingsDirectory = PATH_PROVIDER.getSettingsDirectory();
        if (!settingsDirectory.exists()) {
            if (!settingsDirectory.mkdirs()) {
                System.out.println("ERROR: Could not create settings directory: " + settingsDirectory.getCanonicalPath());
                throw new IOException("Could not create settings directory: " + settingsDirectory.getCanonicalPath());
            }
        }
        final File walletAddressBook = new File(settingsDirectory, Constants.WALLET_ADDRESS_BOOK_FILENAME);
        if (!walletAddressBook.exists()) {
            System.out.println(
                    "HUSH GUI wallet's address book file " + walletAddressBook.getCanonicalPath() +
                            " does not exist. It will be created with default donation team address."
            );
            final PrintStream configOut = new PrintStream(new FileOutputStream(walletAddressBook));
            configOut.println(
                    String.format("%s,%s", Constants.HUSH_DONATION_ADDRESS, "HUSH Team Donation address")
            );
            configOut.close();
        }
    }
}
