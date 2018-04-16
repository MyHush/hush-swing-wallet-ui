// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

class SendCashPanel extends WalletTabPanel {
    private final HushCommandLineBridge cliBridge;
    private final StatusUpdateErrorReporter errorReporter;

    private JComboBox balanceAddressCombo;
    private final JPanel comboBoxParentPanel;
    private String[][] lastAddressBalanceData = null;
    private final DataGatheringThread<String[][]> addressBalanceGatheringThread;

    private final JTextField destinationAddressField;
    private final JTextField destinationAmountField;
    private final JTextField destinationMemoField;
    private final JTextField transactionFeeField;

    private final JButton sendButton;

    private final JLabel operationStatusLabel;
    private final JProgressBar operationStatusProhgressBar;
    private Timer operationStatusTimer = null;
    private String operationStatusID = null;
    private int operationStatusCounter = 0;

    SendCashPanel(final HushCommandLineBridge cliBridge, final StatusUpdateErrorReporter errorReporter) {
        this.cliBridge = cliBridge;
        this.errorReporter = errorReporter;

        // Build content
        this.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        this.setLayout(new BorderLayout());
        final JPanel sendCashPanel = new JPanel();
        this.add(sendCashPanel, BorderLayout.NORTH);
        sendCashPanel.setLayout(new BoxLayout(sendCashPanel, BoxLayout.Y_AXIS));
        sendCashPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

        JPanel tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(new JLabel("Send cash from:       "));
        tempPanel.add(new JLabel(
                "<html><span style=\"font-size:8px;\">" +
                "* Only addresses with a confirmed balance are shown as sources for sending!" +
                "</span>  "
        ));
        sendCashPanel.add(tempPanel);

        balanceAddressCombo = new JComboBox<>(new String[]{ "" });
        comboBoxParentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        comboBoxParentPanel.add(balanceAddressCombo);
        sendCashPanel.add(comboBoxParentPanel);

        JLabel dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 3));
        sendCashPanel.add(dividerLabel);

        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(new JLabel("Destination address:"));
        sendCashPanel.add(tempPanel);

        destinationAddressField = new JTextField(73);
        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(destinationAddressField);
        sendCashPanel.add(tempPanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 3));
        sendCashPanel.add(dividerLabel);

        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(new JLabel("Memo (optional):     "));
        tempPanel.add(new JLabel(
                "<html><span style=\"font-size:8px;\">" +
                "* Memo may be specified only if the destination is a Z (Private) address!" +
                "</span>  "
        ));
        sendCashPanel.add(tempPanel);

        destinationMemoField = new JTextField(73);
        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(destinationMemoField);
        sendCashPanel.add(tempPanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 3));
        sendCashPanel.add(dividerLabel);

        // Construct a more complex panel for the amount and transaction fee
        final JPanel amountAndFeePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        final JPanel amountPanel = new JPanel(new BorderLayout());
        amountPanel.add(new JLabel("Amount to send:"), BorderLayout.NORTH);
        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(destinationAmountField = new JTextField(13));
        destinationAmountField.setHorizontalAlignment(SwingConstants.RIGHT);
        tempPanel.add(new JLabel(" HUSH    "));
        amountPanel.add(tempPanel, BorderLayout.SOUTH);

        final JPanel feePanel = new JPanel(new BorderLayout());
        feePanel.add(new JLabel("Transaction fee:"), BorderLayout.NORTH);
        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(transactionFeeField = new JTextField(13));
        transactionFeeField.setText("0.0001"); // Default value
        transactionFeeField.setHorizontalAlignment(SwingConstants.RIGHT);
        tempPanel.add(new JLabel(" HUSH"));
        feePanel.add(tempPanel, BorderLayout.SOUTH);

        amountAndFeePanel.add(amountPanel);
        amountAndFeePanel.add(feePanel);
        sendCashPanel.add(amountAndFeePanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 3));
        sendCashPanel.add(dividerLabel);

        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(sendButton = new JButton("Send   \u27A4\u27A4\u27A4"));
        sendCashPanel.add(tempPanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 5));
        sendCashPanel.add(dividerLabel);

        final JPanel warningPanel = new JPanel();
        warningPanel.setLayout(new BorderLayout(7, 3));
        final JLabel warningLabel = new JLabel(
                "<html><span style=\"font-size:8px;\">" +
                " * When sending cash from a T (Transparent) address, the remining unspent balance is sent to another " +
                "auto-generated T address. When sending from a Z (Private) address, the remining unspent balance remains with " +
                "the Z address. In both cases the original sending address cannot be used for sending again until the " +
                "transaction is confirmed. The address is temporarily removed from the list! Freshly mined coins may only " +
                "be sent to a Z (Private) address.</span>"
        );
        warningPanel.add(warningLabel, BorderLayout.NORTH);
        sendCashPanel.add(warningPanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 15));
        sendCashPanel.add(dividerLabel);

        // Build the operation status panel
        final JPanel operationStatusPanel = new JPanel();
        sendCashPanel.add(operationStatusPanel);
        operationStatusPanel.setLayout(new BoxLayout(operationStatusPanel, BoxLayout.Y_AXIS));

        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(new JLabel("Last operation status: "));
        tempPanel.add(operationStatusLabel = new JLabel("N/A"));
        operationStatusPanel.add(tempPanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 6));
        operationStatusPanel.add(dividerLabel);

        tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tempPanel.add(new JLabel("Progress: "));
        tempPanel.add(operationStatusProhgressBar = new JProgressBar(0, 200));
        operationStatusProhgressBar.setPreferredSize(new Dimension(250, 17));
        operationStatusPanel.add(tempPanel);

        dividerLabel = new JLabel("   ");
        dividerLabel.setFont(new Font("Helvetica", Font.PLAIN, 13));
        operationStatusPanel.add(dividerLabel);

        // Wire the buttons
        sendButton.addActionListener(event -> {
            try {
                SendCashPanel.this.sendCash();
            } catch (final Exception e) {
                e.printStackTrace();

                final String errMessage;
                if (e instanceof HushCommandLineBridge.WalletCallException) {
                    errMessage = e.getMessage().replace(",", ",\n");
                } else {
                    errMessage = "";
                }
                JOptionPane.showMessageDialog(
                        SendCashPanel.this.getRootPane().getParent(),
                        "An unexpected error occurred when sending cash!\n" +
                        "Please ensure that the HUSH daemon is running and\n" +
                        "parameters are correct. You may try again later...\n" +
                        errMessage,
                        "Error in sending cash",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        // Update the balances via timer and data gathering thread
        this.addressBalanceGatheringThread = new DataGatheringThread<>(
                () -> {
                    final long start = System.currentTimeMillis();
                    final String[][] data = SendCashPanel.this.getAddressPositiveBalanceDataFromWallet();
                    final long end = System.currentTimeMillis();
                    System.out.println("Gathering of address/balance table data done in " + (end - start) + "ms.");
                    return data;
                },
                this.errorReporter, 10000, true
        );
        this.threads.add(addressBalanceGatheringThread);

        final ActionListener alBalancesUpdater = event -> {
            try {
                // TODO: if the user has opened the combo box - this closes it (maybe fix)
                SendCashPanel.this.updateWalletAddressPositiveBalanceComboBox();
            } catch (final Exception e) {
                e.printStackTrace();
                SendCashPanel.this.errorReporter.reportError(e);
            }
        };
        final Timer timerBalancesUpdater = new Timer(15000, alBalancesUpdater);
        timerBalancesUpdater.setInitialDelay(3000);
        timerBalancesUpdater.start();
        this.timers.add(timerBalancesUpdater);

        // Add a popup menu to the destination address field - for convenience
        final JMenuItem paste = new JMenuItem("Paste address");
        final JPopupMenu popupMenu = new JPopupMenu();
        popupMenu.add(paste);
        paste.addActionListener(event -> {
            try {
                final String address = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                if ((address != null) && (address.trim().length() > 0)) {
                    SendCashPanel.this.destinationAddressField.setText(address);
                }
            } catch (final Exception e) {
                e.printStackTrace();
                // TODO: clipboard exception handling - do it better
                // java.awt.datatransfer.UnsupportedFlavorException: Unicode String
                //SendCashPanel.this.errorReporter.reportError(ex);
            }
        });

        this.destinationAddressField.addMouseListener(new MouseAdapter() {
            public void mousePressed(final MouseEvent event) {
                if (!event.isConsumed() && event.isPopupTrigger()) {
                    popupMenu.show(event.getComponent(), event.getPoint().x, event.getPoint().y);
                    event.consume();
                }
            }

            public void mouseReleased(final MouseEvent event) {
                if (!event.isConsumed() && event.isPopupTrigger()) {
                    mousePressed(event);
                }
            }
        });
    }


    private void sendCash() throws HushCommandLineBridge.WalletCallException, IOException, InterruptedException {
        if (balanceAddressCombo.getItemCount() <= 0) {
            JOptionPane.showMessageDialog(
                    SendCashPanel.this.getRootPane().getParent(),
                    "There are no addresses with a positive balance to send cash from!",
                    "No funds available",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // BRX-TODO: Create custom error pane that includes CSS max-width for messages so we don't need to litter
        // BRX-TODO: "\n" in most of our messages
        if (this.balanceAddressCombo.getSelectedIndex() < 0) {
            JOptionPane.showMessageDialog(
                    SendCashPanel.this.getRootPane().getParent(),
                    "Please select a source address with a current positive balance to send cash from!",
                    "Please select source address",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        final String sourceAddress = this.lastAddressBalanceData[this.balanceAddressCombo.getSelectedIndex()][1];
        final String destinationAddress = this.destinationAddressField.getText();
        final String memo = this.destinationMemoField.getText();
        final String amount = this.destinationAmountField.getText();
        final String fee = this.transactionFeeField.getText();

        // Verify general correctness.
        String errorMessage = null;

        if ((sourceAddress == null) || (sourceAddress.trim().length() <= 20)) {
            errorMessage = "Source address is invalid; it is too short or missing.";
        } else if (sourceAddress.length() > 512) {
            errorMessage = "Source address is invalid; it is too long.";
        }

        // TODO: full address validation
        if ((destinationAddress == null) || (destinationAddress.trim().length() <= 0)) {
            errorMessage = "Destination address is invalid; it is missing.";
        } else if (destinationAddress.trim().length() <= 20) {
            errorMessage = "Destination address is invalid; it is too short.";
        } else if (destinationAddress.length() > 512) {
            errorMessage = "Destination address is invalid; it is too long.";
        }

        if ((amount == null) || (amount.trim().length() == 0)) {
            errorMessage = "Amount to send is invalid; it is missing.";
        } else {
            try {
                Double.valueOf(amount);
            } catch (NumberFormatException nfe) {
                errorMessage = "Amount to send is invalid; it is not a number.";
            }
        }

        if ((fee == null) || (fee.trim().length() == 0)) {
            errorMessage = "Transaction fee is invalid; it is missing.";
        } else {
            try {
                Double.valueOf(fee);
            } catch (NumberFormatException nfe) {
                errorMessage = "Transaction fee is invalid; it is not a number.";
            }
        }

        if (errorMessage != null) {
            JOptionPane.showMessageDialog(
                    SendCashPanel.this.getRootPane().getParent(),
                    errorMessage,
                    "Sending parameters are incorrect",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // Check for encrypted wallet
        final boolean walletIsEncrypted = this.cliBridge.isWalletEncrypted();
        if (walletIsEncrypted) {
            final PasswordDialog passwordDialog = new PasswordDialog((JFrame) (SendCashPanel.this.getRootPane().getParent()));
            passwordDialog.setVisible(true);

            if (!passwordDialog.isOKPressed()) {
                return;
            }
            this.cliBridge.unlockWallet(passwordDialog.getPassword());
        }

        // Call the wallet send method
        operationStatusID = this.cliBridge.sendCash(sourceAddress, destinationAddress, amount, memo, fee);

        // Disable controls after send
        sendButton.setEnabled(false);
        balanceAddressCombo.setEnabled(false);
        destinationAddressField.setEnabled(false);
        destinationAmountField.setEnabled(false);
        destinationMemoField.setEnabled(false);
        transactionFeeField.setEnabled(false);

        // Start a timer to update the progress of the operation
        operationStatusCounter = 0;
        operationStatusTimer = new Timer(2000, event -> {
            try {
                // TODO: Handle errors in case of restarted server while wallet is sending ...
                if (cliBridge.isSendingOperationComplete(operationStatusID)) {
                    if (cliBridge.isCompletedOperationSuccessful(operationStatusID)) {
                        operationStatusLabel.setText("<html><span style=\"color:green;font-weight:bold\">SUCCESSFUL</span></html>");

                        JOptionPane.showMessageDialog(
                                SendCashPanel.this.getRootPane().getParent(),
                                "Succesfully sent " + amount + " HUSH from address: \n" +
                                sourceAddress + "\n" + "to address: \n" + destinationAddress + "\n",
                                "Cash sent successfully",
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        final String operationErrorMessage = cliBridge.getOperationFinalErrorMessage(operationStatusID);
                        operationStatusLabel.setText("<html><span style=\"color:red;font-weight:bold\">ERROR: " + operationErrorMessage + "</span></html>");

                        JOptionPane.showMessageDialog(
                                SendCashPanel.this.getRootPane().getParent(),
                                "An error occurred when sending cash. Error message is:\n" +
                                operationErrorMessage + "\n\n" +
                                "Please ensure that sending parameters are correct. You may try again later...\n",
                                "Error in sending cash", JOptionPane.ERROR_MESSAGE
                        );
                    }

                    // Lock the wallet again
                    if (walletIsEncrypted) {
                        SendCashPanel.this.cliBridge.lockWallet();
                    }

                    // Restore controls etc.
                    operationStatusCounter = 0;
                    operationStatusID = null;
                    operationStatusTimer.stop();
                    operationStatusTimer = null;
                    operationStatusProhgressBar.setValue(0);

                    sendButton.setEnabled(true);
                    balanceAddressCombo.setEnabled(true);
                    destinationAddressField.setEnabled(true);
                    destinationAmountField.setEnabled(true);
                    transactionFeeField.setEnabled(true);
                    destinationMemoField.setEnabled(true);
                } else {
                    // Update the progress
                    operationStatusLabel.setText("<html><span style=\"color:orange;font-weight:bold\">IN PROGRESS</span></html>");
                    operationStatusCounter += 2;
                    final int progress;
                    if (operationStatusCounter <= 100) {
                        progress = operationStatusCounter;
                    } else {
                        progress = 100 + (((operationStatusCounter - 100) * 6) / 10);
                    }
                    operationStatusProhgressBar.setValue(progress);
                }
                SendCashPanel.this.repaint();
            } catch (final Exception e) {
                e.printStackTrace();
                SendCashPanel.this.errorReporter.reportError(e);
            }
        });
        operationStatusTimer.setInitialDelay(0);
        operationStatusTimer.start();
    }


    public void prepareForSending(final String address) {
        destinationAddressField.setText(address);
    }


    private void updateWalletAddressPositiveBalanceComboBox() {
        final String[][] newAddressBalanceData = this.addressBalanceGatheringThread.getLastData();

        // The data may be null if nothing is yet obtained
        if (newAddressBalanceData == null) {
            return;
        }
        lastAddressBalanceData = newAddressBalanceData;

        final String[] comboBoxItems = new String[lastAddressBalanceData.length];
        for (int i = 0; i < lastAddressBalanceData.length; i++) {
            // Do numeric formatting or else we may get 1.1111E-5
            comboBoxItems[i] = String.format(
                    "%s - %s",
                    new DecimalFormat("########0.00######").format(Double.valueOf(lastAddressBalanceData[i][0])),
                    lastAddressBalanceData[i][1]
            );
        }

        final int selectedIndex = balanceAddressCombo.getSelectedIndex();
        final boolean isEnabled = balanceAddressCombo.isEnabled();
        this.comboBoxParentPanel.remove(balanceAddressCombo);
        balanceAddressCombo = new JComboBox<>(comboBoxItems);
        comboBoxParentPanel.add(balanceAddressCombo);
        if ((balanceAddressCombo.getItemCount() > 0) && (selectedIndex >= 0) && (balanceAddressCombo.getItemCount() > selectedIndex)) {
            balanceAddressCombo.setSelectedIndex(selectedIndex);
        }
        balanceAddressCombo.setEnabled(isEnabled);

        this.validate();
        this.repaint();
    }


    private String[][] getAddressPositiveBalanceDataFromWallet()
            throws HushCommandLineBridge.WalletCallException, IOException, InterruptedException {
        // Z Addresses - they are OK
        final String[] zAddresses = cliBridge.getWalletZAddresses();

        // T Addresses listed with the list received by addr comamnd
        final String[] tAddresses = cliBridge.getWalletAllPublicAddresses();

        // T addresses with unspent outputs - just in case they are different
        final String[] tAddressesWithUnspentOuts = cliBridge.getWalletPublicAddressesWithUnspentOutputs();

        // Store all known addresses
        final java.util.List<String> allAddresses = new ArrayList<>(Arrays.asList(tAddresses));
        allAddresses.addAll(Arrays.asList(tAddressesWithUnspentOuts));
        allAddresses.addAll(Arrays.asList(zAddresses));

        final List<String[]> addressBalances = new ArrayList<>();

        for (final String address : allAddresses) {
            final String balance = cliBridge.getBalanceForAddress(address);
            if (Double.valueOf(balance) > 0) {
                addressBalances.add(new String[]{ balance, address });
            }
        }

        return (String[][])addressBalances.toArray();
    }
}
