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
                 final HushCommandLineBridge caller
    ) {
        super(rowData, columnNames);
        int accelaratorKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        JMenuItem obtainPrivateKey = new JMenuItem("Obtain private key...");
        obtainPrivateKey.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, accelaratorKeyMask));
        popupMenu.add(obtainPrivateKey);

        obtainPrivateKey.addActionListener(e -> {
            if ((lastRow >= 0) && (lastColumn >= 0)) {
                try {
                    String address = AddressTable.this.getModel().getValueAt(lastRow, 2).toString();

                    // TODO: We need a much more precise criterion to distinguish T/Z adresses;
                    boolean isZAddress = address.startsWith("z") && address.length() > 40;

                    // Check for encrypted wallet
                    final boolean bEncryptedWallet = caller.isWalletEncrypted();
                    if (bEncryptedWallet) {
                        PasswordDialog pd = new PasswordDialog((JFrame) (AddressTable.this.getRootPane().getParent()));
                        pd.setVisible(true);

                        if (!pd.isOKPressed()) {
                            return;
                        }

                        caller.unlockWallet(pd.getPassword());
                    }

                    String privateKey = isZAddress ?
                                                caller.getZPrivateKey(address) : caller.getTPrivateKey(address);

                    // Lock the wallet again
                    if (bEncryptedWallet) {
                        caller.lockWallet();
                    }

                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    clipboard.setContents(new StringSelection(privateKey), null);

                    JOptionPane.showMessageDialog(
                            AddressTable.this.getRootPane().getParent(),
                            (isZAddress ? "Z (Private)" : "T (Transparent)") + " address:\n" +
                                    address + "\n" +
                                    "has private key:\n" +
                                    privateKey + "\n\n" +
                                    "The private key has also been copied to the clipboard.",
                            "Private key information", JOptionPane.INFORMATION_MESSAGE);


                } catch (Exception ex) {
                    ex.printStackTrace();
                    // TODO: report exception to user
                }
            } else {
                // Log perhaps
            }
        });
    } // End constructor

}
