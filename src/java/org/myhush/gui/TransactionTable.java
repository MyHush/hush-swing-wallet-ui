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
    TransactionTable(final Object[][] rowData, final Object[] columnNames,
                     final JFrame parent, final HushCommandLineBridge caller
                    ) {
        super(rowData, columnNames);
        int accelaratorKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        JMenuItem showDetails = new JMenuItem("Show details...");
        showDetails.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, accelaratorKeyMask));
        popupMenu.add(showDetails);

        showDetails.addActionListener(e -> {
            if ((lastRow >= 0) && (lastColumn >= 0)) {
                try {
                    String txID = TransactionTable.this.getModel().getValueAt(lastRow, 6).toString();
                    txID = txID.replaceAll("\"", ""); // In case it has quotes

                    System.out.println("Transaction ID for detail dialog is: " + txID);
                    Map<String, String> details = caller.getRawTransactionDetails(txID);
                    String rawTrans = caller.getRawTransaction(txID);

                    DetailsDialog dd = new DetailsDialog(parent, details);
                    dd.setVisible(true);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // TODO: report exception to user
                }
            } else {
                // Log perhaps
            }
        });


        JMenuItem showInExplorer = new JMenuItem("Show in block explorer");
        showInExplorer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, accelaratorKeyMask));
        popupMenu.add(showInExplorer);

        showInExplorer.addActionListener(e -> {
            if ((lastRow >= 0) && (lastColumn >= 0)) {
                try {
                    String txID = TransactionTable.this.getModel().getValueAt(lastRow, 6).toString();
                    txID = txID.replaceAll("\"", ""); // In case it has quotes

                    System.out.println("Transaction ID for block explorer is: " + txID);
                    Desktop.getDesktop().browse(
                            new URL("http://explorer.myhush.org/tx/" + txID).toURI());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // TODO: report exception to user
                }
            } else {
                // Log perhaps
            }
        });

        JMenuItem showMemoField = new JMenuItem("Show transaction memo");
        showMemoField.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, accelaratorKeyMask));
        popupMenu.add(showMemoField);

        showMemoField.addActionListener(e -> {
            if ((lastRow >= 0) && (lastColumn >= 0)) {
                Cursor oldCursor = parent.getCursor();
                try {
                    String txID = TransactionTable.this.getModel().getValueAt(lastRow, 6).toString();
                    txID = txID.replaceAll("\"", ""); // In case it has quotes

                    String acc = TransactionTable.this.getModel().getValueAt(lastRow, 5).toString();
                    acc = acc.replaceAll("\"", ""); // In case it has quotes

                    // TODO: We need a much more precise criterion to distinguish T/Z adresses;
                    boolean isZAddress = acc.startsWith("z") && acc.length() > 40;
                    if (!isZAddress) {
                        JOptionPane.showMessageDialog(
                                parent,
                                "The selected transaction does not have as destination a Z (private) \n" +
                                        "address or it is unkonwn (not listed) and thus no memo information \n" +
                                        "about this transaction is available.",
                                "Memo information is unavailable",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }


                    System.out.println("Transaction ID for Memo field is: " + txID);
                    System.out.println("Account for Memo field is: " + acc);
                    parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    // TODO: some day support outgoing Z transactions
                    String MemoField = caller.getMemoField(acc, txID);
                    parent.setCursor(oldCursor);
                    System.out.println("Memo field is: " + MemoField);

                    if (MemoField != null) {
                        JOptionPane.showMessageDialog(
                                parent,
                                "The memo contained in the transaction is: \n" + MemoField,
                                "Memo", JOptionPane.PLAIN_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(
                                parent,
                                "The selected transaction does not contain a memo field.",
                                "Memo field is not available...",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    parent.setCursor(oldCursor);
                    ex.printStackTrace();
                    // TODO: report exception to user
                }
            } else {
                // Log perhaps
            }
        });

    } // End constructor


    private static class DetailsDialog
            extends JDialog {
        DetailsDialog(JFrame parent, Map<String, String> details) {
            this.setTitle("Transaction details...");
            this.setSize(600, 310);
            this.setLocation(100, 100);
            this.setLocationRelativeTo(parent);
            this.setModal(true);
            this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

            this.getContentPane().setLayout(new BorderLayout(0, 0));

            JPanel tempPanel = new JPanel(new BorderLayout(0, 0));
            tempPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            JLabel infoLabel = new JLabel(
                    "<html><span style=\"font-size:9px;\">" +
                            "The table shows the information about the transaction with technical details as " +
                            "they appear at HUSH network level." +
                            "</span>");
            infoLabel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
            tempPanel.add(infoLabel, BorderLayout.CENTER);
            this.getContentPane().add(tempPanel, BorderLayout.NORTH);

            String[] columns = new String[]{ "Name", "Value" };
            String[][] data = new String[details.size()][2];
            int i = 0;
            int maxPreferredWidht = 400;
            for (Entry<String, String> ent : details.entrySet()) {
                if (maxPreferredWidht < (ent.getValue().length() * 6)) {
                    maxPreferredWidht = ent.getValue().length() * 6;
                }

                data[i][0] = ent.getKey();
                data[i][1] = ent.getValue();
                i++;
            }

            Arrays.sort(data, new Comparator<String[]>() {
                public int compare(String[] o1, String[] o2) {
                    return o1[0].compareTo(o2[0]);
                }

                public boolean equals(Object obj) {
                    return false;
                }
            });

            DataTable table = new DataTable(data, columns);
            table.getColumnModel().getColumn(0).setPreferredWidth(200);
            table.getColumnModel().getColumn(1).setPreferredWidth(maxPreferredWidht);
            table.setAutoResizeMode(AUTO_RESIZE_OFF);
            JScrollPane tablePane = new JScrollPane(
                    table, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

            this.getContentPane().add(tablePane, BorderLayout.CENTER);

            // Lower close button
            JPanel closePanel = new JPanel();
            closePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
            JButton closeButon = new JButton("Close");
            closePanel.add(closeButon);
            this.getContentPane().add(closePanel, BorderLayout.SOUTH);

            closeButon.addActionListener(e -> {
                DetailsDialog.this.setVisible(false);
                DetailsDialog.this.dispose();
            });

        }


    }
}
