// Copyright (c) 2016 https://github.com/zlatinb
// Copyright (c) 2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class AddressBookPanel extends JPanel {
    private final List<AddressBookEntry> entries = new ArrayList<>();
    private final Set<String> names = new HashSet<>();
    private final SendCashPanel sendCashPanel;
    private final JTabbedPane tabs;
    private final JTable table;
    private final JButton sendCashButton;
    private final JButton deleteContactButton;
    private final JButton copyToClipboardButton;

    AddressBookPanel(final SendCashPanel sendCashPanel, final JTabbedPane tabs) throws IOException {
        this.sendCashPanel = sendCashPanel;
        this.tabs = tabs;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.table = buildTable();
        add(new JScrollPane(table));

        this.sendCashButton = createSendCashButton();
        this.deleteContactButton = createDeleteContactButton();
        this.copyToClipboardButton = createCopyToClipboardButton();
        add(buildButtonsPanel(createNewContactButton(), sendCashButton, deleteContactButton, copyToClipboardButton));

        loadEntriesFromDisk();
    }

    private JButton createNewContactButton() {
        final JButton newContactButton = new JButton("New contact...");
        newContactButton.addActionListener(new NewContactActionListener());
        return newContactButton;
    }

    private JButton createSendCashButton() {
        final JButton sendCashButton = new JButton("Send HUSH");
        sendCashButton.addActionListener(new SendCashActionListener());
        sendCashButton.setEnabled(false);
        return sendCashButton;
    }

    private JButton createCopyToClipboardButton() {
        final JButton copyToClipboardButton = new JButton("Copy address to clipboard");
        copyToClipboardButton.setEnabled(false);
        copyToClipboardButton.addActionListener(new CopyToClipboardActionListener());
        return copyToClipboardButton;
    }

    private JButton createDeleteContactButton() {
        final JButton deleteContactButton = new JButton("Delete contact");
        deleteContactButton.setEnabled(false);
        deleteContactButton.addActionListener(new DeleteAddressActionListener());
        return deleteContactButton;
    }

    private JPanel buildButtonsPanel(
            final JButton newContactButton,
            final JButton sendCashButton,
            final JButton copyToClipboardButton,
            final JButton deleteContactButton
    ) {
        final JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        panel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));

        panel.add(newContactButton);
        panel.add(sendCashButton);
        panel.add(copyToClipboardButton);
        panel.add(deleteContactButton);

        return panel;
    }

    private JTable buildTable() {
        final JTable table = new JTable(new AddressBookTableModel(), new DefaultTableColumnModel());
        table.addColumn(new TableColumn(0)); // name column
        table.addColumn(new TableColumn(1)); // address column
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // one at a time
        table.getSelectionModel().addListSelectionListener(new AddressListSelectionListener());
        table.addMouseListener(new AddressMouseListener());
        return table;
    }

    // Check to see if donation address in user's address book is the no-longer-owned-by-team address
    // NOTE: This didn't seem more reasonable to do anywhere else :\
    private boolean isAddressBookLineDisownedDonationAddress(final String line) {
        return line.equals("t1h6kmaQwcuyejDLazT3TNZfV8EEtCzHRhc,HUSH Donation address");
    }

    private String getUpToDateDonationAddressLine() {
        return String.format("%s,%s", Constants.HUSH_DONATION_ADDRESS, "HUSH Team Donation address");
    }

    private void loadEntriesFromDisk() throws IOException {
        final File addressBookFile = new File(App.PATH_PROVIDER.getSettingsDirectory(), Constants.WALLET_ADDRESS_BOOK_FILENAME);
        if (!addressBookFile.exists()) {
            return;
        }
        try (final BufferedReader bufferedReader = new BufferedReader(new FileReader(addressBookFile))) {
            do {
                final String rawLine = bufferedReader.readLine();
                if (rawLine == null) {
                    break;
                }
                final String line;
                if (isAddressBookLineDisownedDonationAddress(rawLine)) {
                    line = getUpToDateDonationAddressLine();
                    // BRX-TODO: Maybe we should trigger a save after this event occurs?
                } else {
                    line = rawLine;
                }
                // format is address,name - this way name can contain commas ;-)
                final int addressEnd = line.indexOf(',');
                if (addressEnd < 0) {
                    throw new IOException("Address book is corrupted!");
                }
                final String address = line.substring(0, addressEnd);
                final String name = line.substring(addressEnd + 1);
                if (!names.add(name)) {
                    continue; // duplicate
                }
                entries.add(new AddressBookEntry(name, address));
            } while (true);
        }
        System.out.println("loaded " + entries.size() + " address book entries");
    }

    private void saveEntriesToDisk() {
        System.out.println("Saving " + entries.size() + " addresses");
        try {
            final File addressBookFile = new File(App.PATH_PROVIDER.getSettingsDirectory(), Constants.WALLET_ADDRESS_BOOK_FILENAME);
            try (final PrintWriter printWriter = new PrintWriter(new FileWriter(addressBookFile))) {
                for (final AddressBookEntry entry : entries) {
                    printWriter.println(entry.address + "," + entry.name);
                }
            }
        } catch (final IOException bad) {
            // TODO: report error to the user!
            bad.printStackTrace();
            System.out.println("Saving address book failed!");
        }
    }

    private static class AddressBookEntry {
        final String name, address;

        AddressBookEntry(final String name, final String address) {
            this.name = name;
            this.address = address;
        }
    }

    private class DeleteAddressActionListener implements ActionListener {
        public void actionPerformed(final ActionEvent event) {
            final int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            final AddressBookEntry entry = entries.get(row);
            entries.remove(row);
            names.remove(entry.name);
            deleteContactButton.setEnabled(false);
            sendCashButton.setEnabled(false);
            copyToClipboardButton.setEnabled(false);
            table.repaint();
            SwingUtilities.invokeLater(() -> saveEntriesToDisk());
        }
    }

    private class CopyToClipboardActionListener implements ActionListener {
        public void actionPerformed(final ActionEvent event) {
            final int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            final AddressBookEntry entry = entries.get(row);
            final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(entry.address), null);
        }
    }

    private class NewContactActionListener implements ActionListener {
        public void actionPerformed(final ActionEvent event) {
            final String name =
                (String) JOptionPane.showInputDialog(
                    AddressBookPanel.this,
                    "Please enter the name of the contact:",
                    "Add new contact step 1",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    ""
                );
            if (name == null || "".equals(name)) {
                return; // cancelled
            }
            // TODO: check for dupes
            names.add(name);

            final String address =
                (String) JOptionPane.showInputDialog(
                    AddressBookPanel.this,
                    "Please enter the t-address or z-address of " + name,
                    "Add new contact step 2",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    ""
                );
            if (address == null || "".equals(address)) {
                return; // cancelled
            }
            entries.add(new AddressBookEntry(name, address));

            SwingUtilities.invokeLater(() -> {
                table.invalidate();
                table.revalidate();
                table.repaint();

                saveEntriesToDisk();
            });
        }
    }

    private class SendCashActionListener implements ActionListener {
        public void actionPerformed(final ActionEvent event) {
            final int row = table.getSelectedRow();
            if (row < 0) {
                return;
            }
            final AddressBookEntry entry = entries.get(row);
            sendCashPanel.prepareForSending(entry.address);
            tabs.setSelectedIndex(2);
        }
    }

    private class AddressMouseListener extends MouseAdapter {
        @Override
        public void mousePressed(final MouseEvent event) {
            if (event.isConsumed() || (!event.isPopupTrigger())) {
                return;
            }
            final int row = table.rowAtPoint(event.getPoint());
            final int column = table.columnAtPoint(event.getPoint());
            table.changeSelection(row, column, false, false);
            final AddressBookEntry entry = entries.get(row);

            final JPopupMenu menu = new JPopupMenu();
            final JMenuItem sendCash = new JMenuItem("Send HUSH to " + entry.name);
            sendCash.addActionListener(new SendCashActionListener());
            menu.add(sendCash);

            final JMenuItem copyAddress = new JMenuItem("Copy address to clipboard");
            copyAddress.addActionListener(new CopyToClipboardActionListener());
            menu.add(copyAddress);

            final JMenuItem deleteEntry = new JMenuItem("Delete " + entry.name + " from contacts");
            deleteEntry.addActionListener(new DeleteAddressActionListener());
            menu.add(deleteEntry);

            menu.show(event.getComponent(), event.getPoint().x, event.getPoint().y);
            event.consume();
        }
    }

    private class AddressListSelectionListener implements ListSelectionListener {
        @Override
        public void valueChanged(final ListSelectionEvent event) {
            final int row = table.getSelectedRow();
            if (row < 0) {
                sendCashButton.setEnabled(false);
                deleteContactButton.setEnabled(false);
                copyToClipboardButton.setEnabled(false);
                return;
            }
            final String name = entries.get(row).name;
            sendCashButton.setText("Send HUSH to " + name);
            sendCashButton.setEnabled(true);
            deleteContactButton.setText("Delete contact " + name);
            deleteContactButton.setEnabled(true);
            copyToClipboardButton.setEnabled(true);
        }

    }

    private class AddressBookTableModel extends AbstractTableModel {
        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(final int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return "name";
                case 1:
                    return "address";
                default:
                    throw new IllegalArgumentException("invalid column " + columnIndex);
            }
        }

        @Override
        public Class<?> getColumnClass(final int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(final int rowIndex, final int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            final AddressBookEntry entry = entries.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return entry.name;
                case 1:
                    return entry.address;
                default:
                    throw new IllegalArgumentException("bad column " + columnIndex);
            }
        }
    }
}