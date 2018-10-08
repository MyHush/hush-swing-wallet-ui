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
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

/**
 * Addresses panel - shows T/Z addresses and their balances.
 */
class AddressesPanel extends WalletTabPanel {
    private String[][] lastAddressBalanceData; // BRX-TODO: Encapsulate
    private final JFrame parentFrame;
    private final HushCommandLineBridge cliBridge;
    private final StatusUpdateErrorReporter errorReporter;
    private JTable addressBalanceTable;
    private JScrollPane addressBalanceTablePane;
    private DataGatheringThread<String[][]> balanceGatheringThread;

    private long lastInteractiveRefresh;

    // Table of validated addresses with their validation result. An invalid or watch-only address should not be shown
    // and should be remembered as invalid here
    private final Map<String, Boolean> validationMap = new HashMap<>();

    AddressesPanel(
            final JFrame parentFrame,
            final HushCommandLineBridge cliBridge,
            final StatusUpdateErrorReporter errorReporter
    ) throws IOException, InterruptedException, HushCommandLineBridge.WalletCallException {
        this.parentFrame = parentFrame;
        this.cliBridge = cliBridge;
        this.errorReporter = errorReporter;

        this.lastInteractiveRefresh = System.currentTimeMillis();

        // Build content
        final JPanel addressesPanel = this;
        addressesPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        addressesPanel.setLayout(new BorderLayout(0, 0));

        // Build panel of buttons
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
        buttonPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

        final JButton newTAddressButton = new JButton("New T (Transparent) address");
        buttonPanel.add(newTAddressButton);
        final JButton newZAddressButton = new JButton("New Z (Private) address");
        buttonPanel.add(newZAddressButton);
        buttonPanel.add(new JLabel("           "));
        final JButton refreshButton = new JButton("Refresh");
        buttonPanel.add(refreshButton);

        addressesPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Table of addresses
        lastAddressBalanceData = getAddressBalanceDataFromWallet();
        addressesPanel.add(
                addressBalanceTablePane = new JScrollPane(
                        addressBalanceTable = this.createAddressBalanceTable(lastAddressBalanceData)
                ),
                BorderLayout.CENTER
        );

        final JPanel warningPanel = new JPanel();
        warningPanel.setLayout(new BorderLayout(3, 3));
        warningPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        final JLabel warningL = new JLabel(
                "<html><span style=\"font-size:8px;\">" +
                "* If the balance of an address is flagged as not confirmed, the address is currently taking " +
                "part in a transaction. The shown balance then is the expected value it will have when " +
                "the transaction is confirmed. The average confirmation time is 2.5 min.</span>"
        );
        warningPanel.add(warningL, BorderLayout.NORTH);
        addressesPanel.add(warningPanel, BorderLayout.NORTH);

        // Thread and timer to update the address/balance table
        this.balanceGatheringThread = new DataGatheringThread<>(
                () -> {
                    final long start = System.currentTimeMillis();
                    final String[][] data = AddressesPanel.this.getAddressBalanceDataFromWallet();
                    final long end = System.currentTimeMillis();
                    System.out.println("Gathering of address/balance table data done in " + (end - start) + "ms.");
                    return data;
                },
                this.errorReporter, 25000
        );
        this.threads.add(this.balanceGatheringThread);

        final ActionListener alBalances = actionEvent -> {
            try {
                AddressesPanel.this.updateWalletAddressBalanceTableAutomated();
            } catch (final Exception e) {
                e.printStackTrace();
                AddressesPanel.this.errorReporter.reportError(e);
            }
        };
        final Timer timer = new Timer(5000, alBalances);
        timer.start();
        this.timers.add(timer);

        // Button actions
        refreshButton.addActionListener(event -> {
            final Cursor oldCursor = AddressesPanel.this.getCursor();
            try {
                // TODO: dummy progress bar ... maybe
                AddressesPanel.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                AddressesPanel.this.updateWalletAddressBalanceTableInteractive();
                AddressesPanel.this.setCursor(oldCursor);
            } catch (final Exception e) {
                AddressesPanel.this.setCursor(oldCursor);
                e.printStackTrace();
                AddressesPanel.this.errorReporter.reportError(e, false);
            }
        });
        newTAddressButton.addActionListener(actionEvent -> createNewAddress(false));
        newZAddressButton.addActionListener(actionEvent -> createNewAddress(true));
    }

    // Null if not selected
    public String getSelectedAddress() {
        final int selectedRow = this.addressBalanceTable.getSelectedRow();
        if (selectedRow != -1) {
            return this.addressBalanceTable.getModel().getValueAt(selectedRow, 2).toString();
        }
        return null;
    }

    private void createNewAddress(final boolean isZAddress) {
        try {
            // Check for encrypted wallet
            final boolean walletIsEncrypted = this.cliBridge.isWalletEncrypted();
            if (walletIsEncrypted && isZAddress) {
                final PasswordDialog dialog = new PasswordDialog((JFrame) (this.getRootPane().getParent()));
                dialog.setVisible(true);

                if (!dialog.isOKPressed()) {
                    return;
                }
                this.cliBridge.unlockWallet(dialog.getPassword());
            }

            final String address = this.cliBridge.createNewAddress(isZAddress);

            // Lock the wallet again
            if (walletIsEncrypted && isZAddress) {
                this.cliBridge.lockWallet();
            }

            JOptionPane.showMessageDialog(
                this.getRootPane().getParent(),
                "A new " + (isZAddress ? "Z (Private)" : "T (Transparent)") + " address has been created cuccessfully:\n" + address,
                "Address created", JOptionPane.INFORMATION_MESSAGE
            );
            this.updateWalletAddressBalanceTableInteractive();
        } catch (final Exception e) {
            e.printStackTrace();
            AddressesPanel.this.errorReporter.reportError(e, false);
        }
    }

    // Interactive and non-interactive are mutually exclusive
    private synchronized void updateWalletAddressBalanceTableInteractive()
            throws HushCommandLineBridge.WalletCallException, IOException, InterruptedException {
        this.lastInteractiveRefresh = System.currentTimeMillis();

        final String[][] newAddressBalanceData = this.getAddressBalanceDataFromWallet();

        if (!Arrays.deepEquals(lastAddressBalanceData, newAddressBalanceData)) {
            System.out.println("Updating table of addresses/balances I...");
            this.remove(addressBalanceTablePane);
            this.add(
                addressBalanceTablePane = new JScrollPane(
                    addressBalanceTable = this.createAddressBalanceTable(newAddressBalanceData)
                ),
                BorderLayout.CENTER
            );
            lastAddressBalanceData = newAddressBalanceData;

            this.validate();
            this.repaint();
        }
    }


    // Interactive and non-interactive are mutually exclusive
    private synchronized void updateWalletAddressBalanceTableAutomated() {
        // Make sure it is > 1 min since the last interactive refresh
        if ((System.currentTimeMillis() - lastInteractiveRefresh) < (60 * 1000)) {
            return;
        }
        final String[][] newAddressBalanceData = this.balanceGatheringThread.getLastData();

        if ((newAddressBalanceData != null) && !Arrays.deepEquals(lastAddressBalanceData, newAddressBalanceData)) {
            System.out.println("Updating table of addresses/balances A...");
            this.remove(addressBalanceTablePane);
            this.add(
                addressBalanceTablePane = new JScrollPane(
                    addressBalanceTable = this.createAddressBalanceTable(newAddressBalanceData)
                ),
                BorderLayout.CENTER
            );
            lastAddressBalanceData = newAddressBalanceData;
            this.validate();
            this.repaint();
        }
    }


    private JTable createAddressBalanceTable(final String rowData[][]) {
        final String columnNames[] = { "Balance", "Confirmed?", "Address" };
        final JTable table = new AddressTable(rowData, columnNames, this.cliBridge);
        table.setDefaultEditor(Object.class, null);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(1000);

        return table;
    }

    private static final String CONFIRMED_SPECIAL_CHAR  = App.SPECIAL_CHARACTER_PROVIDER.getConfirmedBalanceSymbol();
    private static final String UNCONFIRMED_SPECIAL_CHAR = App.SPECIAL_CHARACTER_PROVIDER.getUnconfirmedBalanceSymbol();
    // Format double numbers - else sometimes we get exponential notation 1E-4 ZEC
    private static final DecimalFormat BALANCE_FORMATTER = new DecimalFormat("########0.00######");

    private String[] getAddressBalanceDisplayData(final String address, boolean watchOnlyOrInvalid)
            throws InterruptedException, HushCommandLineBridge.WalletCallException, IOException
    {
        final String addressToDisplay;
        if (watchOnlyOrInvalid) {
            System.out.println(String.format("The following address is invalid or a watch-only address: %s. It will not be displayed!", address));
            addressToDisplay = "<INVALID OR WATCH-ONLY ADDRESS> !!!";
        } else {
            addressToDisplay = address;
        }

        final String confirmedBalance = cliBridge.getBalanceForAddress(address);
        final String unconfirmedBalance = cliBridge.getUnconfirmedBalanceForAddress(address);
        final boolean isConfirmed = confirmedBalance.equals(unconfirmedBalance);
        final String balanceToShow = BALANCE_FORMATTER.format(Double.valueOf(isConfirmed ? confirmedBalance : unconfirmedBalance));

        return new String[]{
                balanceToShow,
                isConfirmed ? ("Yes  " + CONFIRMED_SPECIAL_CHAR) : ("No  " + UNCONFIRMED_SPECIAL_CHAR),
                addressToDisplay
        };
    }

    private String[][] getAddressBalanceDataFromWallet()
            throws HushCommandLineBridge.WalletCallException, IOException, InterruptedException
    {
        // Z Addresses - they are OK
        final String[] zAddresses = cliBridge.getWalletZAddresses();

        // T Addresses listed with the list received by addr comamnd
        final String[] tAddresses = cliBridge.getWalletAllPublicAddresses();

        // T addresses with unspent outputs - just in case they are different
        final String[] tAddressesWithUnspentOuts = cliBridge.getWalletPublicAddressesWithUnspentOutputs();

        // Store all known T addresses
        final List<String> tAddressesCombined = new ArrayList<>(Arrays.asList(tAddresses));
        tAddressesCombined.addAll(Arrays.asList(tAddressesWithUnspentOuts));

        final List<String[]> addressBalances = new ArrayList<>();

        for (final String address : tAddressesCombined) {
            // Make sure the current address is not watch-only or invalid
            if (!this.validationMap.containsKey(address)) {
                final boolean validationResult = this.cliBridge.isWatchOnlyOrInvalidAddress(address);
                this.validationMap.put(address, validationResult);

                // if (validationResult) {
                //     JOptionPane.showMessageDialog(
                //         this.parentFrame,
                //         "An invalid or watch-only address exists in the wallet:" + "\n" + address + "\n\n" +
                //         "The GUI wallet software cannot operate properly with addresses that are invalid or\n" +
                //         "exist in the wallet as watch-only addresses. Do NOT use this address as a destination\n" +
                //         "address for payment operations!",
                //         "Error: invalid or watch-only address exists!",
                //         JOptionPane.ERROR_MESSAGE
                //     );
                // }
            }

            final boolean watchOnlyOrInvalid = this.validationMap.get(address);
            if (!watchOnlyOrInvalid) {
                addressBalances.add(getAddressBalanceDisplayData(address, watchOnlyOrInvalid));
            }
        }

        // BRX-TODO: Logic is duplicated here as just above, merge?
        for (final String address : zAddresses) {
            addressBalances.add(getAddressBalanceDisplayData(address, false));
        }
        String[][] addarray = new String[addressBalances.size()][];
        return addressBalances.toArray(addarray);
    }
}
