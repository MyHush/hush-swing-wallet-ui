// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog to get the user password - to encrypt a wallet
 */
public class PasswordEncryptionDialog extends PasswordDialog {
    private JTextField passwordConfirmationField;

    PasswordEncryptionDialog(JFrame parent) {
        super(parent);

        this.upperLabel.setText(
                "<html>The wallet.dat file will be encrypted with a password. If the operation is successful, " +
                "hushd will automatically stop and will need to be restarted. The GUI wallet will also be stopped " +
                "and will need to be restarted. Please enter the password:</html>"
        );

        final JLabel confLabel = new JLabel("Confirmation: ");
        this.freeSlotPanel.add(confLabel);
        this.freeSlotPanel.add(passwordConfirmationField = new JPasswordField(30));
        this.passwordLabel.setPreferredSize(confLabel.getPreferredSize());

        final JLabel dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 8));
        this.freeSlotPanel2.add(dividerLabel);

        this.setSize(460, 270);
        this.validate();
        this.repaint();
    }


    protected void processOK() {
        final String password;
        if (this.passwordField.getText() == null) {
            password = "";
        } else {
            password = this.passwordField.getText();
        }

        final String confirmation;
        if (this.passwordConfirmationField.getText() == null) {
            confirmation = "";
        } else {
            confirmation = this.passwordConfirmationField.getText();
        }

        if (!password.equals(confirmation)) {
            JOptionPane.showMessageDialog(
                    this.getParent(),
                    "The password and the confirmation do not match!",
                    "Password mismatch...",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }
        super.processOK();
    }
}
