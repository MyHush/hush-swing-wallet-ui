// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Table to be used for transactions, addresses, etc.
 */
class DataTable extends JTable {
    int lastRow = -1;
    int lastColumn = -1;

    final JPopupMenu popupMenu;

    DataTable(final Object[][] rowData, final Object[] columnNames) {
        super(rowData, columnNames);

        popupMenu = new JPopupMenu();
        int accelaratorKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        JMenuItem copy = new JMenuItem("Copy value");
        popupMenu.add(copy);
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, accelaratorKeyMask));
        copy.addActionListener(e -> {
            if ((lastRow >= 0) && (lastColumn >= 0)) {
                String text = DataTable.this.getValueAt(lastRow, lastColumn).toString();

                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(new StringSelection(text), null);
            } else {
                // Log perhaps
            }
        });


        JMenuItem exportToCSV = new JMenuItem("Export data to CSV...");
        popupMenu.add(exportToCSV);
        exportToCSV.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, accelaratorKeyMask));
        exportToCSV.addActionListener(e -> {
            try {
                DataTable.this.exportToCSV();
            } catch (Exception ex) {
                ex.printStackTrace();
                // TODO: better error handling
                JOptionPane.showMessageDialog(
                        DataTable.this.getRootPane().getParent(),
                        "An unexpected error occurred when exporting data to CSV file.\n" +
                                "\n" +
                                ex.getMessage(),
                        "Error in CSV export", JOptionPane.ERROR_MESSAGE);
            }
        });


        this.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if ((!e.isConsumed()) && e.isPopupTrigger()) {
                    JTable table = (JTable) e.getSource();
                    lastColumn = table.columnAtPoint(e.getPoint());
                    lastRow = table.rowAtPoint(e.getPoint());

                    if (!table.isRowSelected(lastRow)) {
                        table.changeSelection(lastRow, lastColumn, false, false);
                    }

                    popupMenu.show(e.getComponent(), e.getPoint().x, e.getPoint().y);
                    e.consume();
                } else {
                    lastColumn = -1;
                    lastRow = -1;
                }
            }

            public void mouseReleased(MouseEvent e) {
                if ((!e.isConsumed()) && e.isPopupTrigger()) {
                    mousePressed(e);
                }
            }
        });

//        this.addKeyListener(new KeyAdapter() 
//		{			
//			@Override
//			public void keyTyped(KeyEvent e) 
//			{
//				if (e.getKeyCode() == KeyEvent.VK_CONTEXT_MENU)
//				{
//					System.out.println("Context menu invoked...");;
//					popupMenu.show(e.getComponent(), e.getComponent().getX(), e.getComponent().getY());
//				}
//			}
//		});
    }


    // Exports the table data to a CSV file
    private void exportToCSV()
            throws IOException {
        final String ENCODING = "UTF-8";

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export data to CSV file...");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        int result = fileChooser.showSaveDialog(this.getRootPane().getParent());

        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File f = fileChooser.getSelectedFile();

        FileOutputStream fos = new FileOutputStream(f);
        fos.write(new byte[]{ (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });

        // Write header
        StringBuilder header = new StringBuilder();
        for (int i = 0; i < this.getColumnCount(); i++) {
            String columnName = this.getColumnName(i);
            header.append(columnName);

            if (i < (this.getColumnCount() - 1)) {
                header.append(",");
            }
        }
        header.append("\n");
        fos.write(header.toString().getBytes(ENCODING));

        // Write rows
        for (int row = 0; row < this.getRowCount(); row++) {
            StringBuilder rowBuf = new StringBuilder();
            for (int col = 0; col < this.getColumnCount(); col++) {
                rowBuf.append(this.getValueAt(row, col).toString());

                if (col < (this.getColumnCount() - 1)) {
                    rowBuf.append(",");
                }
            }
            rowBuf.append("\n");
            fos.write(rowBuf.toString().getBytes(ENCODING));
        }

        fos.close();

        JOptionPane.showMessageDialog(
                this.getRootPane().getParent(),
                "The data has been exported successfully as CSV to location:\n" +
                        f.getCanonicalPath(),
                "Export successful...", JOptionPane.INFORMATION_MESSAGE);
    }
}
