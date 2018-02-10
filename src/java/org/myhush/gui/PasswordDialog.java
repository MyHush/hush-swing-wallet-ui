// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog to get the user password for encrypted wallets - for unlock
 */
class PasswordDialog extends JDialog {
    private boolean isOKPressed = false;
    private String password = null;

    JLabel passwordLabel;
    JTextField passwordField;
    final JLabel upperLabel;
    final JPanel freeSlotPanel;
    final JPanel freeSlotPanel2;

    PasswordDialog(JFrame parent) {
        super(parent);

        this.setTitle("Password...");
        this.setLocation(parent.getLocation().x + 50, parent.getLocation().y + 50);
        this.setModal(true);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel tempPanel = new JPanel(new BorderLayout(0, 0));
        tempPanel.add(this.upperLabel = new JLabel("<html>The wallet is encrypted and protected with a password. " +
                                                           "Please enter the password to unlock it temporarily during " +
                                                           "the operation</html>"), BorderLayout.CENTER);
        controlsPanel.add(tempPanel);

        JLabel dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 8));
        controlsPanel.add(dividerLabel);

        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(passwordLabel = new JLabel("Password: "));
        tempPanel.add(passwordField = new JPasswordField(30));
        controlsPanel.add(tempPanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 8));
        controlsPanel.add(dividerLabel);

        this.freeSlotPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        controlsPanel.add(this.freeSlotPanel);

        this.freeSlotPanel2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        controlsPanel.add(this.freeSlotPanel2);

        tempPanel = new JPanel(new BorderLayout(0, 0));
        tempPanel.add(new JLabel(
                        "<html><span style=\"font-weight:bold\">" +
                                "WARNING: Never enter your password on a public/shared " +
                                "computer or one that you suspect has been infected with malware! " +
                                "</span></html>"
                ), BorderLayout.CENTER
                     );
        controlsPanel.add(tempPanel);

        this.getContentPane().setLayout(new BorderLayout(0, 0));
        this.getContentPane().add(controlsPanel, BorderLayout.NORTH);

        // Form buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
        JButton okButon = new JButton("OK");
        buttonPanel.add(okButon);
        buttonPanel.add(new JLabel("   "));
        JButton cancelButon = new JButton("Cancel");
        buttonPanel.add(cancelButon);
        this.getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        okButon.addActionListener(e -> PasswordDialog.this.processOK());

        cancelButon.addActionListener(e -> {
            PasswordDialog.this.setVisible(false);
            PasswordDialog.this.dispose();

            PasswordDialog.this.isOKPressed = false;
            PasswordDialog.this.password = null;
        });

        this.setSize(450, 190);
        this.validate();
        this.repaint();
    }

    void processOK() {
        String pass = PasswordDialog.this.passwordField.getText();

        if ((pass == null) || (pass.trim().length() <= 0)) {
            JOptionPane.showMessageDialog(
                    PasswordDialog.this.getParent(),
                    "The password is empty. Please enter it into the text field.", "Empty...",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        PasswordDialog.this.setVisible(false);
        PasswordDialog.this.dispose();

        PasswordDialog.this.isOKPressed = true;
        PasswordDialog.this.password = pass;
    }

    public boolean isOKPressed() {
        return this.isOKPressed;
    }

    public String getPassword() {
        return this.password;
    }
}
