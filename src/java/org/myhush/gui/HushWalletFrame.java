// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import org.myhush.gui.environment.system.DaemonInfoProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

/**
 * Main wallet window
 */
class HushWalletFrame extends JFrame {
    private final DashboardPanel dashboard;
    private final AddressesPanel addresses;
    private final SendCashPanel sendPanel;

    HushWalletFrame(
            final StartupProgressDialog progressDialog,
            final DaemonInfoProvider daemonInfoProvider,
            final HushCommandLineBridge commandLineBridge
    ) throws IOException, InterruptedException, HushCommandLineBridge.WalletCallException {
        super("HUSH Wallet " + App.APP_VERSION_LONG);

        if (progressDialog != null) {
            progressDialog.setProgressText("Starting GUI wallet...");
        }
        final ClassLoader classLoader = this.getClass().getClassLoader();

        this.setIconImage(new ImageIcon(classLoader.getResource("images/hush-logo-sm.png")).getImage());

        final Container contentPane = this.getContentPane();
        final StatusUpdateErrorReporter errorReporter = new StatusUpdateErrorReporter(this);

        // Build content
        final JTabbedPane tabs = new JTabbedPane();
        final Font oldTabFont = tabs.getFont();
        final Font newTabFont = new Font(oldTabFont.getName(), Font.BOLD | Font.ITALIC, oldTabFont.getSize() * 57 / 50);
        tabs.setFont(newTabFont);
        tabs.addTab("Overview ",
                new ImageIcon(classLoader.getResource("images/icon-overview.png")),
                dashboard = new DashboardPanel(this, daemonInfoProvider, commandLineBridge, errorReporter)
        );
        tabs.addTab("Own addresses ",
                new ImageIcon(classLoader.getResource("images/icon-own-addresses.png")),
                addresses = new AddressesPanel(this, commandLineBridge, errorReporter)
        );
        tabs.addTab("Send cash ",
                new ImageIcon(classLoader.getResource("images/icon-send.png")),
                sendPanel = new SendCashPanel(commandLineBridge, errorReporter)
        );
        tabs.addTab("Address book ",
                new ImageIcon(classLoader.getResource("images/icon-address-book.png")),
                new AddressBookPanel(sendPanel, tabs)
        );
        contentPane.add(tabs);

        final WalletOperations walletOps = new WalletOperations(
                this, tabs, dashboard, addresses, sendPanel, commandLineBridge, errorReporter
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
                final File warningFlagFile = new File(App.PATH_PROVIDER.getSettingsDirectory(), "initialInfoShown.flag");
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
