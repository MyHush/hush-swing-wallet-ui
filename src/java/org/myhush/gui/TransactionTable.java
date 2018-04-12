// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Table to be used for transactions - specifically.
 */
class TransactionTable extends DataTable {
    TransactionTable(
            final Object[][] rowData,
            final Object[] columnNames,
            final JFrame parent,
            final HushCommandLineBridge cliBridge
    ) {
        super(rowData, columnNames);
        final int acceleratorKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        final JMenuItem showDetails = new JMenuItem("Show details...");
        showDetails.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, acceleratorKeyMask));
        popupMenu.add(showDetails);

        showDetails.addActionListener(event -> {
            if (lastRow < 0 || lastColumn < 0) {
                // log?
                return;
            }
            try {
                final String txID = TransactionTable.this.getModel().getValueAt(lastRow, 6).toString().replaceAll("\"", "");
                System.out.println("Transaction ID for detail dialog is: " + txID);
                final Map<String, String> details = cliBridge.getRawTransactionDetails(txID);
                final String rawTrans = cliBridge.getRawTransaction(txID);
                new DetailsDialog(parent, details).setVisible(true);
            } catch (final Exception e) {
                e.printStackTrace();
                // TODO: report exception to user
            }
        });

        final JMenuItem showInExplorer = new JMenuItem("Show in block explorer");
        showInExplorer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, acceleratorKeyMask));
        popupMenu.add(showInExplorer);

        showInExplorer.addActionListener(event -> {
            if (lastRow < 0 || lastColumn < 0) {
                // log?
                return;
            }
            try {
                final String txID = TransactionTable.this.getModel().getValueAt(lastRow, 6).toString().replaceAll("\"", "");
                System.out.println("Transaction ID for block explorer is: " + txID);
                Desktop.getDesktop().browse(new URL("http://explorer.myhush.org/tx/" + txID).toURI()); // BRX-TODO: Move base URL to a configuration file
            } catch (final Exception e) {
                e.printStackTrace();
                // TODO: report exception to user
            }
        });

        final JMenuItem showMemoField = new JMenuItem("Show transaction memo");
        showMemoField.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, acceleratorKeyMask));
        popupMenu.add(showMemoField);

        showMemoField.addActionListener(event -> {
            if (lastRow < 0 || lastColumn < 0) {
                // log?
                return;
            }
            final Cursor oldCursor = parent.getCursor();
            try {
                final String txID = TransactionTable.this.getModel().getValueAt(lastRow, 6).toString().replaceAll("\"", "");
                final String acc = TransactionTable.this.getModel().getValueAt(lastRow, 5).toString().replaceAll("\"", "");

                // TODO: We need a much more precise criterion to distinguish T/Z adresses;
                final boolean isZAddress = acc.startsWith("z") && acc.length() > 40;
                if (!isZAddress) {
                    JOptionPane.showMessageDialog(
                            parent,
                            "The selected transaction does not have as destination a Z (private) \n" +
                            "address or it is unkonwn (not listed) and thus no memo information \n" +
                            "about this transaction is available.",
                            "Memo information is unavailable",
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }
                System.out.println("Transaction ID for Memo field is: " + txID);
                System.out.println("Account for Memo field is: " + acc);
                parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                // TODO: some day support outgoing Z transactions
                final String memoField = cliBridge.getMemoField(acc, txID);
                parent.setCursor(oldCursor);
                System.out.println("Memo field is: " + memoField);

                if (memoField != null) {
                    JOptionPane.showMessageDialog(
                            parent,
                            "The memo contained in the transaction is: \n" + memoField,
                            "Memo",
                            JOptionPane.PLAIN_MESSAGE
                    );
                } else {
                    JOptionPane.showMessageDialog(
                            parent,
                            "The selected transaction does not contain a memo field.",
                            "Memo field is not available...",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            } catch (final Exception e) {
                parent.setCursor(oldCursor);
                e.printStackTrace();
                // TODO: report exception to user
            }
        });
    }


    private static class DetailsDialog extends JDialog {
        DetailsDialog(final JFrame parent, final Map<String, String> details) {
            this.setTitle("Transaction details...");
            this.setSize(600, 310);
            this.setLocation(100, 100);
            this.setLocationRelativeTo(parent);
            this.setModal(true);
            this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            this.getContentPane().setLayout(new BorderLayout(0, 0));

            final JPanel tempPanel = new JPanel(new BorderLayout(0, 0));
            tempPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            final JLabel infoLabel = new JLabel(
                    "<html><span style=\"font-size:9px;\">" +
                    "The table shows the information about the transaction with technical details as " +
                    "they appear at HUSH network level.</span>"
            );
            infoLabel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            tempPanel.add(infoLabel, BorderLayout.CENTER);
            this.getContentPane().add(tempPanel, BorderLayout.NORTH);

            // BRX-TODO: This is gross, use tuples?
            final String[] columns = new String[]{ "Name", "Value" };
            final String[][] data = new String[details.size()][2];
            int i = 0;
            int maxPreferredWidth = 400;
            for (final Entry<String, String> ent : details.entrySet()) {
                if (maxPreferredWidth < (ent.getValue().length() * 6)) {
                    maxPreferredWidth = ent.getValue().length() * 6;
                }
                data[i][0] = ent.getKey();
                data[i][1] = ent.getValue();
                i++;
            }

            Arrays.sort(data, new Comparator<String[]>() {
                public int compare(final String[] apple, final String[] orange) {
                    return apple[0].compareTo(orange[0]);
                }

                public boolean equals(final Object obj) {
                    return false;
                }
            });

            final DataTable table = new DataTable(data, columns);
            table.setDefaultEditor(Object.class, null);
            table.getColumnModel().getColumn(0).setPreferredWidth(200);
            table.getColumnModel().getColumn(1).setPreferredWidth(maxPreferredWidth);
            table.setAutoResizeMode(AUTO_RESIZE_OFF);
            final JScrollPane tablePane = new JScrollPane(
                    table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            );
            this.getContentPane().add(tablePane, BorderLayout.CENTER);

            // Lower close button
            final JPanel closePanel = new JPanel();
            closePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
            final JButton closeButon = new JButton("Close");
            closePanel.add(closeButon);
            this.getContentPane().add(closePanel, BorderLayout.SOUTH);

            closeButon.addActionListener(event -> {
                DetailsDialog.this.setVisible(false);
                DetailsDialog.this.dispose();
            });
        }
    }
}
