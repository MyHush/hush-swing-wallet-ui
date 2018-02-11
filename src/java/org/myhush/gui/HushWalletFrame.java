// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import org.myhush.gui.HushDaemonObserver.DAEMON_STATUS;
import org.myhush.gui.HushDaemonObserver.DaemonInfo;
import org.myhush.gui.HushDaemonObserver.InstallationDetectionException;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/**
 * Main wallet window
 */
class HushWalletFrame extends JFrame {
    private final DashboardPanel dashboard;
    private final AddressesPanel addresses;
    private final SendCashPanel sendPanel;

    private HushWalletFrame(final StartupProgressDialog progressDialog)
            throws IOException, InterruptedException, HushCommandLineBridge.WalletCallException {
        super("HUSH Wallet v0.71.1 (beta)");

        if (progressDialog != null) {
            progressDialog.setProgressText("Starting GUI wallet...");
        }
        final ClassLoader classLoader = this.getClass().getClassLoader();

        this.setIconImage(new ImageIcon(classLoader.getResource("images/hush-logo-sm.png")).getImage());

        final Container contentPane = this.getContentPane();
        final StatusUpdateErrorReporter errorReporter = new StatusUpdateErrorReporter(this);
        final HushDaemonObserver installationObserver = new HushDaemonObserver(OSUtil.getProgramDirectory());
        final HushCommandLineBridge cliBridge = new HushCommandLineBridge(OSUtil.getProgramDirectory());

        // Build content
        final JTabbedPane tabs = new JTabbedPane();
        final Font oldTabFont = tabs.getFont();
        final Font newTabFont = new Font(oldTabFont.getName(), Font.BOLD | Font.ITALIC, oldTabFont.getSize() * 57 / 50);
        tabs.setFont(newTabFont);
        tabs.addTab("Overview ",
                new ImageIcon(classLoader.getResource("images/icon-overview.png")),
                dashboard = new DashboardPanel(this, installationObserver, cliBridge, errorReporter)
        );
        tabs.addTab("Own addresses ",
                new ImageIcon(classLoader.getResource("images/icon-own-addresses.png")),
                addresses = new AddressesPanel(this, cliBridge, errorReporter)
        );
        tabs.addTab("Send cash ",
                new ImageIcon(classLoader.getResource("images/icon-send.png")),
                sendPanel = new SendCashPanel(cliBridge, errorReporter)
        );
        tabs.addTab("Address book ",
                new ImageIcon(classLoader.getResource("images/icon-address-book.png")),
                new AddressBookPanel(sendPanel, tabs)
        );
        contentPane.add(tabs);

        final WalletOperations walletOps = new WalletOperations(
                this, tabs, dashboard, addresses, sendPanel, cliBridge, errorReporter
        );
        this.setSize(new Dimension(870, 427));

        // Build menu
        final JMenuBar menuBar = new JMenuBar();
        final JMenu mainMenu = new JMenu("Main");
        mainMenu.setMnemonic(KeyEvent.VK_M);
        final int acceleratorKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        final JMenuItem menuItemAbout = new JMenuItem("About...", KeyEvent.VK_T);
        mainMenu.add(menuItemAbout);
        menuItemAbout.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, acceleratorKeyMask));
        mainMenu.addSeparator();
        final JMenuItem menuItemExit = new JMenuItem("Quit", KeyEvent.VK_Q);
        mainMenu.add(menuItemExit);
        menuItemExit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, acceleratorKeyMask));
        menuBar.add(mainMenu);

        final JMenu walletMenu = new JMenu("Wallet");
        walletMenu.setMnemonic(KeyEvent.VK_W);
        final JMenuItem menuItemBackup = new JMenuItem("Backup...", KeyEvent.VK_B);
        walletMenu.add(menuItemBackup);
        menuItemBackup.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, acceleratorKeyMask));
        final JMenuItem menuItemEncrypt = new JMenuItem("Encrypt...", KeyEvent.VK_E);
        walletMenu.add(menuItemEncrypt);
        menuItemEncrypt.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, acceleratorKeyMask));
        final JMenuItem menuItemExportKeys = new JMenuItem("Export private keys...", KeyEvent.VK_K);
        walletMenu.add(menuItemExportKeys);
        menuItemExportKeys.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, acceleratorKeyMask));
        final JMenuItem menuItemImportKeys = new JMenuItem("Import private keys...", KeyEvent.VK_I);
        walletMenu.add(menuItemImportKeys);
        menuItemImportKeys.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, acceleratorKeyMask));
        final JMenuItem menuItemShowPrivateKey = new JMenuItem("Show private key...", KeyEvent.VK_P);
        walletMenu.add(menuItemShowPrivateKey);
        menuItemShowPrivateKey.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, acceleratorKeyMask));
        final JMenuItem menuItemImportOnePrivateKey = new JMenuItem("Import one private key...", KeyEvent.VK_N);
        walletMenu.add(menuItemImportOnePrivateKey);
        menuItemImportOnePrivateKey.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, acceleratorKeyMask));
        menuBar.add(walletMenu);

        // TODO: Temporarily disable encryption until further notice - Oct 24 2016
        // BRX-TODO: ^^^ ???
        menuItemEncrypt.setEnabled(false);

        this.setJMenuBar(menuBar);

        // Add listeners etc.
        menuItemExit.addActionListener(actionEvent -> HushWalletFrame.this.exitProgram());
        menuItemAbout.addActionListener(actionEvent -> new AboutDialog(HushWalletFrame.this).setVisible(true));
        menuItemBackup.addActionListener(actionEvent -> walletOps.backupWallet());
        menuItemEncrypt.addActionListener(actionEvent -> walletOps.encryptWallet());
        menuItemExportKeys.addActionListener(actionEvent -> walletOps.exportWalletPrivateKeys());
        menuItemImportKeys.addActionListener(actionEvent -> walletOps.importWalletPrivateKeys());
        menuItemShowPrivateKey.addActionListener(actionEvent -> walletOps.showPrivateKey());
        menuItemImportOnePrivateKey.addActionListener(actionEvent -> walletOps.importSinglePrivateKey());

        // Close operation
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent event) {
                HushWalletFrame.this.exitProgram();
            }
        });

        // Show initial message
        SwingUtilities.invokeLater(() -> {
            try {
                final String userDir = OSUtil.getSettingsDirectory();
                final File warningFlagFile = new File(userDir + File.separator + "initialInfoShown.flag");
                if (warningFlagFile.exists()) {
                    return;
                } else {
                    warningFlagFile.createNewFile();
                }

            } catch (IOException e) {
                /* TODO: report exceptions to the user */
                e.printStackTrace();
            }

            JOptionPane.showMessageDialog(
                    HushWalletFrame.this.getRootPane().getParent(),
                    "The HUSH GUI Wallet is currently considered experimental. Use of this software\n" +
                    "comes at your own risk! Be sure to read the list of known issues and limitations\n" +
                    "at this page: https://github.com/MyHush/hush-swing-wallet-ui\n\n" +
                    "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
                    "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
                    "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n" +
                    "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n" +
                    "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n" +
                    "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN\n" +
                    "THE SOFTWARE.\n\n" + "(This message will be shown only once)",
                    "Disclaimer",
                    JOptionPane.INFORMATION_MESSAGE
            );
        });

        // Finally dispose of the progress dialog
        if (progressDialog != null) {
            progressDialog.doDispose();
        }
    }

    // BRX-TODO: Move main() out of this class
    public static void main(String argv[]) throws IOException {
        try {
            final OSUtil.OS_TYPE os = OSUtil.getOSType();

            // On Windows/Mac we log to a file only! - users typically do not use consoles
            if (os == OSUtil.OS_TYPE.WINDOWS || os == OSUtil.OS_TYPE.MAC_OS) {
                redirectLoggingToFile();
            }
            if (os != OSUtil.OS_TYPE.WINDOWS) {
                possiblyCreateHUSHConfigFile(); // this is not run because on Win we have a batch file
                // BRX-TODO: Remove batch file and handle this back in this GUI client again
            }

            System.out.println("Starting HUSH Swing Wallet ...");
            System.out.println("OS: " + System.getProperty("os.name") + " = " + os);
            System.out.println("Current directory: " + new File(".").getCanonicalPath());
            System.out.println("Class path: " + System.getProperty("java.class.path"));
            System.out.println("Environment PATH: " + System.getenv("PATH"));

            // Look and feel settings - for now a custom OS-look and feel is set for Windows,
            // Mac OS will follow later.
            if (os == OSUtil.OS_TYPE.WINDOWS) {
                // Custom Windows L&F and font settings
                UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");

                // This font looks good but on Windows 7 it misses some chars like the stars...
                //FontUIResource font = new FontUIResource("Lucida Sans Unicode", Font.PLAIN, 11);
                //UIManager.put("Table.font", font);
            } else {
                for (final LookAndFeelInfo lookAndFeelInfo : UIManager.getInstalledLookAndFeels()) {
                    System.out.println("Available look and feel: " + lookAndFeelInfo.getName() + " " + lookAndFeelInfo.getClassName());
                    if (lookAndFeelInfo.getName().equals("Nimbus")) {
                        UIManager.setLookAndFeel(lookAndFeelInfo.getClassName());
                        break;
                    }
                }
            }

            // If hushd is currently not running, do a startup of the daemon as a child process
            // It may be started but not ready - then also show dialog
            final DaemonInfo hushdInfo;
            {
                final HushDaemonObserver initialInstallationObserver = new HushDaemonObserver(OSUtil.getProgramDirectory());
                hushdInfo = initialInstallationObserver.getDaemonInfo();
            }

            final HushCommandLineBridge initialCliBridge = new HushCommandLineBridge(OSUtil.getProgramDirectory());
            final StartupProgressDialog startupBar;
            {
                boolean daemonStartInProgress = false;
                try {
                    if (hushdInfo.status == DAEMON_STATUS.RUNNING) {
                        HushCommandLineBridge.NetworkAndBlockchainInfo info = initialCliBridge.getNetworkAndBlockchainInfo();
                        // If more than 20 minutes behind in the blockchain - startup in progress
                        if ((System.currentTimeMillis() - info.lastBlockDate.getTime()) > (20 * 60 * 1000)) {
                            System.out.println("Current blockchain synchronization date is" +
                                                       new Date(info.lastBlockDate.getTime()));
                            daemonStartInProgress = true;
                        }
                    }
                } catch (HushCommandLineBridge.WalletCallException wce) {
                    if ((wce.getMessage().contains("{\"code\":-28")) || // Started but not ready
                                (wce.getMessage().contains("error code: -28"))) {
                        System.out.println("hushd is currently starting...");
                        daemonStartInProgress = true;
                    }
                }

                if ((hushdInfo.status != DAEMON_STATUS.RUNNING) || daemonStartInProgress) {
                    System.out.println(
                            "hushd is not runing at the moment or has not started/synchronized 100% - showing splash..."
                    );
                    startupBar = new StartupProgressDialog(initialCliBridge);
                    startupBar.setVisible(true);
                    startupBar.waitForStartup();
                } else {
                    startupBar = null;
                }
            }

            // Main GUI is created here
            new HushWalletFrame(startupBar).setVisible(true);
        } catch (final InstallationDetectionException ide) {
            ide.printStackTrace();

            JOptionPane.showMessageDialog(
                    null,
                    "This program was started in directory: " +
                    OSUtil.getProgramDirectory() + "\n" + ide.getMessage() + "\n" +
                    "See the console output for more detailed error information!",
                    "Installation error",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        } catch (final HushCommandLineBridge.WalletCallException wce) {
            wce.printStackTrace();

            if (wce.getMessage().contains("{\"code\":-28,\"message\"") || wce.getMessage().contains("error code: -28")) {
                JOptionPane.showMessageDialog(
                        null,
                        "It appears that hushd has been started but is not ready to accept wallet\n" +
                        "connections. It is still loading the wallet and blockchain. Please try to\n" +
                        "start the GUI wallet later...",
                        "Wallet communication error",
                        JOptionPane.ERROR_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(
                        null,
                        "There was a problem communicating with the HUSH daemon/wallet. \n" +
                        "Please ensure that the HUSH server hushd is started (e.g. via \n" +
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
        final String settingsDirPath = OSUtil.getSettingsDirectory();
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

        System.setOut(fileOut);
        System.setErr(fileOut);
    }

    private static void possiblyCreateHUSHConfigFile() throws IOException {
        final String blockchainDirPath = OSUtil.getBlockchainDirectory();
        final File blockchainDir = new File(blockchainDirPath);

        if (!blockchainDir.exists()) {
            if (!blockchainDir.mkdirs()) {
                System.out.println("ERROR: Could not create settings directory: " + blockchainDir.getCanonicalPath());
                throw new IOException("Could not create settings directory: " + blockchainDir.getCanonicalPath());
            }
        }
        final File hushConfigFile = new File(blockchainDir, "hush.conf");

        if (!hushConfigFile.exists()) {
            System.out.println(
                    "HUSH configuration file " + hushConfigFile.getCanonicalPath() +
                   " does not exist. It will be created with default settings."
            );
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
            configOut.println("# The rpcuser/rpcpassword are used for the local call to hushd");
            configOut.println("rpcuser=User" + (random.nextInt() & Integer.MAX_VALUE));
            configOut.println(String.format("rpcpassword=Pass%d%d", (random.nextInt() & Integer.MAX_VALUE), (random.nextInt() & Integer.MAX_VALUE)));
            configOut.println("addnode=explorer.myhush.org");
            configOut.println("addnode=stilgar.leto.net");
            configOut.println("addnode=zdash.suprnova.cc");
            configOut.println("addnode=dnsseed.myhush.org");
            configOut.close();
        }
    }

    public void exitProgram() {
        System.out.println("Exiting ...");
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        this.dashboard.stopThreadsAndTimers();
        this.addresses.stopThreadsAndTimers();
        this.sendPanel.stopThreadsAndTimers();

//        Integer blockchainProgress = this.dashboard.getBlockchainPercentage();
//
//        if ((blockchainProgress != null) && (blockchainProgress >= 100))
//        {
//            this.dashboard.waitForEndOfThreads(3000);
//            this.addresses.waitForEndOfThreads(3000);
//            this.sendPanel.waitForEndOfThreads(3000);
//        }

        HushWalletFrame.this.setVisible(false);
        HushWalletFrame.this.dispose();
        System.exit(0);
    }
}
