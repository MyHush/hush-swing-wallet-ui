// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog to enter a single private key to import
 */
class SingleKeyImportDialog extends JDialog {
    private final JTextField keyField;
    private final JProgressBar progress;

    private final HushCommandLineBridge cliBridge;

    private final JButton okButton;
    private final JButton cancelButton;

    SingleKeyImportDialog(final JFrame parent, final HushCommandLineBridge cliBridge) {
        super(parent);
        this.cliBridge = cliBridge;

        this.setTitle("Enter private key...");
        this.setLocation(parent.getLocation().x + 50, parent.getLocation().y + 50);
        this.setModal(true);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        final JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel tempPanel = new JPanel(new BorderLayout(0, 0));
        tempPanel.add(new JLabel("<html>Please enter a single private key to import.</html>"), BorderLayout.CENTER);
        controlsPanel.add(tempPanel);

        JLabel dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 8));
        controlsPanel.add(dividerLabel);

        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(new JLabel("Key: "));
        tempPanel.add(keyField = new JTextField(60));
        controlsPanel.add(tempPanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 8));
        controlsPanel.add(dividerLabel);

        tempPanel = new JPanel(new BorderLayout(0, 0));
        tempPanel.add(new JLabel(
                        "<html><span style=\"font-weight:bold\">Warning:</span> " +
                        "Private key import is a slow operation that " +
                        "requires blockchain rescanning (may take many minutes). The GUI " +
                        "will not be usable for other functions during this time</html>"
                ),
                BorderLayout.CENTER
        );
        controlsPanel.add(tempPanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 8));
        controlsPanel.add(dividerLabel);

        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(progress = new JProgressBar());
        controlsPanel.add(tempPanel);

        this.getContentPane().setLayout(new BorderLayout(0, 0));
        this.getContentPane().add(controlsPanel, BorderLayout.NORTH);

        // Form buttons
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
        okButton = new JButton("Import");
        buttonPanel.add(okButton);
        buttonPanel.add(new JLabel("   "));
        cancelButton = new JButton("Cancel");
        buttonPanel.add(cancelButton);
        this.getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        okButton.addActionListener(event -> SingleKeyImportDialog.this.processOK());

        cancelButton.addActionListener(event -> {
            SingleKeyImportDialog.this.setVisible(false);
            SingleKeyImportDialog.this.dispose();
        });

        this.setSize(740, 210);
        this.validate();
        this.repaint();
    }


    private void processOK() {
        final String key = SingleKeyImportDialog.this.keyField.getText();

        if ((key == null) || (key.trim().length() == 0)) {
            JOptionPane.showMessageDialog(
                    SingleKeyImportDialog.this.getParent(),
                    "The key is empty. Please enter it into the text field.", "Empty...",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Start import
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.progress.setIndeterminate(true);
        this.progress.setValue(1);

        this.okButton.setEnabled(false);
        this.cancelButton.setEnabled(false);

        SingleKeyImportDialog.this.keyField.setEditable(false);

        new Thread(() -> {
            try {
                SingleKeyImportDialog.this.cliBridge.importPrivateKey(key);

                JOptionPane.showMessageDialog(
                        SingleKeyImportDialog.this,
                        "The private key:\n" + key + "\n" + "has been imported successfully.",
                        "Private key imported successfully...",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (final Exception e) {
                e.printStackTrace();

                JOptionPane.showMessageDialog(
                        SingleKeyImportDialog.this.getRootPane().getParent(),
                        "An error occurred when importing private key. Error message is:\n" +
                        e.getClass().getName() + ":\n" + e.getMessage() + "\n\n" +
                        "Please ensure that hushd is running and the key is in the correct \n" +
                        "form. You may try again later...\n",
                        "Error in importing private key",
                        JOptionPane.ERROR_MESSAGE
                );
            } finally {
                SingleKeyImportDialog.this.setVisible(false);
                SingleKeyImportDialog.this.dispose();
            }
        }).start();
    }
}
