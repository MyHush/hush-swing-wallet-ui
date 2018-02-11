// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;

class AddressTable extends DataTable {
    AddressTable(final Object[][] rowData,
                 final Object[] columnNames,
                 final HushCommandLineBridge cliBridge
    ) {
        super(rowData, columnNames);
        final int acceleratorKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        final JMenuItem obtainPrivateKey = new JMenuItem("Obtain private key...");
        obtainPrivateKey.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, acceleratorKeyMask));
        popupMenu.add(obtainPrivateKey);

        obtainPrivateKey.addActionListener(event -> {
            if (lastRow < 0 || lastColumn < 0) {
                // log something?
                return;
            }
            try {
                final String address = AddressTable.this.getModel().getValueAt(lastRow, 2).toString();

                // TODO: We need a much more precise criterion to distinguish T/Z adresses;
                final boolean isZAddress = address.startsWith("z") && address.length() > 40;

                // Check for encrypted wallet
                final boolean walletIsEncrypted = cliBridge.isWalletEncrypted();
                if (walletIsEncrypted) {
                    final PasswordDialog dialog = new PasswordDialog((JFrame) (AddressTable.this.getRootPane().getParent()));
                    dialog.setVisible(true);

                    if (!dialog.isOKPressed()) {
                        return;
                    }
                    cliBridge.unlockWallet(dialog.getPassword());
                }

                final String privateKey = isZAddress ? cliBridge.getZPrivateKey(address) : cliBridge.getTPrivateKey(address);

                // Lock the wallet again
                if (walletIsEncrypted) {
                    cliBridge.lockWallet();
                }
                final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(new StringSelection(privateKey), null);

                JOptionPane.showMessageDialog(
                    AddressTable.this.getRootPane().getParent(),
                    (isZAddress ? "Z (Private)" : "T (Transparent)") + " address:\n" + address + "\n" +
                    "has private key:\n" + privateKey + "\n\n" + "The private key has also been copied to the clipboard.",
                    "Private key information",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } catch (final Exception e) {
                e.printStackTrace();
                // TODO: report exception to user
            }
        });
    }
}
