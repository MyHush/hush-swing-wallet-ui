// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import org.myhush.gui.HushCommandLineBridge.WalletCallException;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;

/**
 * Provides miscellaneous operations for the wallet file.
 */
class WalletOperations {
    private final HushWalletFrame parent;
    private final JTabbedPane tabs;
    private final DashboardPanel dashboard;
    private final SendCashPanel sendCash;
    private final AddressesPanel addresses;

    private final HushCommandLineBridge cliBridge;
    private final StatusUpdateErrorReporter errorReporter;


    WalletOperations(
            final HushWalletFrame parent,
            final JTabbedPane tabs,
            final DashboardPanel dashboard,
            final AddressesPanel addresses,
            final SendCashPanel sendCash,
            final HushCommandLineBridge cliBridge,
            final StatusUpdateErrorReporter errorReporter
    ) {
        this.parent = parent;
        this.tabs = tabs;
        this.dashboard = dashboard;
        this.addresses = addresses;
        this.sendCash = sendCash;
        this.cliBridge = cliBridge;
        this.errorReporter = errorReporter;
    }

    public void encryptWallet() {
        try {
            if (this.cliBridge.isWalletEncrypted()) {
                JOptionPane.showMessageDialog(
                        this.parent,
                        "The wallet.dat file being used is already encrypted. This\n" +
                        "operation may be performed only on a wallet that is not\n" +
                        "yet encrypted!",
                        "Wallet is already encrypted...",
                        JOptionPane.ERROR_MESSAGE
                 );
                return;
            }
            final PasswordEncryptionDialog passwordEncryptionDialog = new PasswordEncryptionDialog(this.parent);
            passwordEncryptionDialog.setVisible(true);

            if (!passwordEncryptionDialog.isOKPressed()) {
                return;
            }

            final Cursor oldCursor = this.parent.getCursor();
            try {
                this.parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                this.dashboard.stopThreadsAndTimers();
                this.sendCash.stopThreadsAndTimers();
                this.cliBridge.encryptWallet(passwordEncryptionDialog.getPassword());
                this.parent.setCursor(oldCursor);
            } catch (final WalletCallException wce) {
                this.parent.setCursor(oldCursor);
                wce.printStackTrace();

                JOptionPane.showMessageDialog(
                        this.parent,
                        "An unexpected error occurred while encrypting the wallet!\n" +
                        "It is recommended to stop and restart both hushd and the GUI wallet!\n\n" +
                        wce.getMessage().replace(",", ",\n"),
                        "Error in encrypting wallet...",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            JOptionPane.showMessageDialog(
                    this.parent,
                    "The wallet has been encrypted sucessfully and hushd has stopped.\n" +
                    "The GUI wallet will be stopped as well. Please restart both. In\n" +
                    "addtion the internal wallet keypool has been flushed. You need\n" +
                    "to make a new backup...\n",
                    "Wallet is now encrypted...",
                    JOptionPane.INFORMATION_MESSAGE
            );
            this.parent.exitProgram();
        } catch (final Exception e) {
            this.errorReporter.reportError(e, false);
        }
    }

    private File getDefaultDirectory() {
        return new File(System.getProperty("user.home"));
    }

    public void backupWallet() {
        try {
            this.issueBackupDirectoryWarning();

            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Backup wallet to file...");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setCurrentDirectory(getDefaultDirectory());

            if (fileChooser.showSaveDialog(this.parent) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            final Cursor oldCursor = this.parent.getCursor();
            final String fileName = fileChooser.getSelectedFile().getName();
            try {
                this.parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                this.cliBridge.backupWallet(fileName);
                this.parent.setCursor(oldCursor);
            } catch (final WalletCallException wce) {
                this.parent.setCursor(oldCursor);
                wce.printStackTrace();

                JOptionPane.showMessageDialog(
                        this.parent,
                        "An unexpected error occurred while backing up the wallet!\n" +
                        wce.getMessage().replace(",", ",\n"),
                        "Error in backing up wallet...",
                        JOptionPane.ERROR_MESSAGE
                 );
                return;
            }

            JOptionPane.showMessageDialog(
                    this.parent,
                    "The wallet has been backed up successfully to file: " + fileName + "\n" +
                    "in the backup directory provided to hushd (-exportdir=<dir>).",
                    "Wallet is backed up...",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (final Exception e) {
            this.errorReporter.reportError(e, false);
        }
    }


    public void exportWalletPrivateKeys() {
        // TODO: Will need corrections once encryption is reenabled!!!
        try {
            this.issueBackupDirectoryWarning();

            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export wallet private keys to file...");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fileChooser.setCurrentDirectory(getDefaultDirectory());

            if (fileChooser.showSaveDialog(this.parent) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            final Cursor oldCursor = this.parent.getCursor();
            final String fileName = fileChooser.getSelectedFile().getName();
            try {
                this.parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                this.cliBridge.exportWallet(fileName);
                this.parent.setCursor(oldCursor);
            } catch (final WalletCallException wce) {
                this.parent.setCursor(oldCursor);
                wce.printStackTrace();

                JOptionPane.showMessageDialog(
                        this.parent,
                        "An unexpected error occurred while exporting wallet private keys!\n" +
                        wce.getMessage().replace(",", ",\n"),
                        "Error in exporting wallet private keys...",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            JOptionPane.showMessageDialog(
                    this.parent,
                    "The wallet private keys have been exported successfully to file:\n" +
                    fileName + "\n" +
                    "in the backup directory provided to hushd (-exportdir=<dir>).\n" +
                    "You need to protect this file from unauthorized access. Anyone who\n" +
                    "has access to the private keys can spend the HUSH balance!",
                    "Wallet private key export...",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (final Exception e) {
            this.errorReporter.reportError(e, false);
        }
    }


    // TODO: Will need corrections once encryption is re-enabled!!!
    public void importWalletPrivateKeys() {
        final int dialogSelection = JOptionPane.showConfirmDialog(
                this.parent,
                "Private key import is a potentially slow operation. It may take\n" +
                "several minutes during which the GUI will be non-responsive.\n" +
                "The data to import must be in the format used by the option:\n" +
                "\"Export private keys...\"\n\n" +
                "Are you sure you wish to import private keys?",
                "Private key import notice...",
                JOptionPane.YES_NO_OPTION
        );
        if (dialogSelection == JOptionPane.NO_OPTION) {
            return;
        }

        try {
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Import wallet private keys from file...");
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            if (fileChooser.showSaveDialog(this.parent) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            final Cursor oldCursor = this.parent.getCursor();
            final String fileCanonicalPath = fileChooser.getSelectedFile().getCanonicalPath();

            try {
                this.parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                this.cliBridge.importWallet(fileCanonicalPath);
                this.parent.setCursor(oldCursor);
            } catch (final WalletCallException wce) {
                this.parent.setCursor(oldCursor);
                wce.printStackTrace();

                JOptionPane.showMessageDialog(
                        this.parent,
                        "An unexpected error occurred while importing wallet private keys!\n" +
                        wce.getMessage().replace(",", ",\n"),
                        "Error in importing wallet private keys...",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            JOptionPane.showMessageDialog(
                    this.parent,
                    "Wallet private keys have been imported successfully from location:\n" +
                    fileCanonicalPath + "\n\n",
                    "Wallet private key import...",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (final Exception e) {
            this.errorReporter.reportError(e, false);
        }
    }


    public void showPrivateKey() {
        if (this.tabs.getSelectedIndex() != 1) {
            JOptionPane.showMessageDialog(
                    this.parent,
                    "Please select an address in the \"Own addresses\" tab to view its private key",
                    "Please select an address...",
                    JOptionPane.INFORMATION_MESSAGE
            );
            this.tabs.setSelectedIndex(1);
            return;
        }
        final String selectedAddress = this.addresses.getSelectedAddress();

        if (selectedAddress == null) {
            JOptionPane.showMessageDialog(
                    this.parent,
                    "Please select an address in the table of addresses " +
                            "to view its private key",
                    "Please select an address...", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            // Check for encrypted wallet
            final boolean walletIsEncrypted = this.cliBridge.isWalletEncrypted();
            if (walletIsEncrypted) {
                final PasswordDialog passwordDialog = new PasswordDialog(this.parent);
                passwordDialog.setVisible(true);

                if (!passwordDialog.isOKPressed()) {
                    return;
                }
                this.cliBridge.unlockWallet(passwordDialog.getPassword());
            }

            // TODO: We need a much more precise criterion to distinguish T/Z adresses;
            final boolean isZAddress = selectedAddress.startsWith("z") && selectedAddress.length() > 40;
            final String privateKey = isZAddress ? this.cliBridge.getZPrivateKey(selectedAddress) : this.cliBridge.getTPrivateKey(selectedAddress);

            // Lock the wallet again
            if (walletIsEncrypted) {
                this.cliBridge.lockWallet();
            }

            final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(privateKey), null);

            JOptionPane.showMessageDialog(
                    this.parent,
                    (isZAddress ? "Z (Private)" : "T (Transparent)") + " address:\n" + selectedAddress + "\n" +
                    "has private key:\n" + privateKey + "\n\n" +
                    "The private key has also been copied to the clipboard.",
                    "Private key information",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (final Exception e) {
            this.errorReporter.reportError(e, false);
        }
    }


    public void importSinglePrivateKey() {
        try {
            new SingleKeyImportDialog(this.parent, this.cliBridge).setVisible(true);
        } catch (final Exception e) {
            this.errorReporter.reportError(e, false);
        }
    }


    private void issueBackupDirectoryWarning() throws IOException {
        final File warningFlagFile = new File(App.PATH_PROVIDER.getSettingsDirectory(), "backupInfoShown.flag");
        if (warningFlagFile.exists()) {
            return;
        } else {
            warningFlagFile.createNewFile();
        }
        JOptionPane.showMessageDialog(
                this.parent,
                "For security reasons the wallet may be backed up/private keys exported only if\n" +
                "the hushd parameter -exportdir=<dir> has been set. If you started hushd\n" +
                "manually, you ought to have provided this parameter. When hushd is started\n" +
                "automatically by the GUI wallet the directory provided as parameter to -exportdir\n" +
                "is the user home directory: " + getDefaultDirectory().getCanonicalPath() + "\n" +
                "Please navigate to the directory provided as -exportdir=<dir> and select a\n" +
                "filename in it to backup/export private keys. If you select another directory\n" +
                "instead, the destination file will still end up in the directory provided as \n" +
                "-exportdir=<dir>. If this parameter was not provided to hushd, the process\n" +
                "will fail with a security check error. The filename needs to consist of only\n" +
                "alphanumeric characters (e.g. dot is not allowed).\n\n" +
                "(This message will be shown only once)",
                "Wallet backup directory information",
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}
