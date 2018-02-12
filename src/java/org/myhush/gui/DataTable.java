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
import java.nio.charset.StandardCharsets;

/**
 * Table to be used for transactions, addresses, etc.
 */
class DataTable extends JTable {
    int lastRow = -1;
    int lastColumn = -1;
    final JPopupMenu popupMenu; // used by child classes

    DataTable(final Object[][] rowData, final Object[] columnNames) {
        super(rowData, columnNames);

        popupMenu = new JPopupMenu();
        final int acceleratorKeyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();

        final JMenuItem copy = new JMenuItem("Copy value");
        popupMenu.add(copy);
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, acceleratorKeyMask));
        copy.addActionListener(event -> {
            if (lastRow < 0 || lastColumn < 0) {
                // log?
                return;
            }
            final String text = DataTable.this.getValueAt(lastRow, lastColumn).toString();
            final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(text), null);
        });

        final JMenuItem exportToCSV = new JMenuItem("Export data to CSV...");
        popupMenu.add(exportToCSV);
        exportToCSV.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, acceleratorKeyMask));
        exportToCSV.addActionListener(event -> {
            try {
                DataTable.this.exportToCSV();
            } catch (final Exception e) {
                e.printStackTrace();
                // TODO: better error handling
                JOptionPane.showMessageDialog(
                    DataTable.this.getRootPane().getParent(),
                    "An unexpected error occurred when exporting data to CSV file.\n\n" + e.getMessage(),
                    "Error in CSV export", JOptionPane.ERROR_MESSAGE
                );
            }
        });

        this.addMouseListener(new MouseAdapter() {
            public void mousePressed(final MouseEvent event) {
                if (event.isConsumed() || !event.isPopupTrigger()) {
                    lastColumn = -1;
                    lastRow = -1;
                    return;
                }
                final JTable table = (JTable) event.getSource();
                lastColumn = table.columnAtPoint(event.getPoint());
                lastRow = table.rowAtPoint(event.getPoint());

                if (!table.isRowSelected(lastRow)) {
                    table.changeSelection(lastRow, lastColumn, false, false);
                }

                popupMenu.show(event.getComponent(), event.getPoint().x, event.getPoint().y);
                event.consume();
            }

            public void mouseReleased(final MouseEvent event) {
                if (!event.isConsumed() && event.isPopupTrigger()) {
                    mousePressed(event);
                }
            }
        });
    }

    // Exports the table data to a CSV file
    private void exportToCSV() throws IOException {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export data to CSV file...");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));

        if (fileChooser.showSaveDialog(this.getRootPane().getParent()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = fileChooser.getSelectedFile();
        final FileOutputStream fileOutputStream = new FileOutputStream(file);
        fileOutputStream.write(new byte[]{ (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });

        // Write header
        final StringBuilder header = new StringBuilder();
        final int columnCount = this.getColumnCount();
        for (int i = 0; i < columnCount; i++) {
            header.append(this.getColumnName(i));

            if (i < (columnCount - 1)) {
                header.append(",");
            }
        }
        header.append("\n");
        fileOutputStream.write(header.toString().getBytes(StandardCharsets.UTF_8));

        // Write rows
        for (int row = 0; row < this.getRowCount(); row++) {
            final StringBuilder rowContent = new StringBuilder();
            for (int col = 0; col < this.getColumnCount(); col++) {
                rowContent.append(this.getValueAt(row, col).toString());

                if (col < (this.getColumnCount() - 1)) {
                    rowContent.append(",");
                }
            }
            rowContent.append("\n");
            fileOutputStream.write(rowContent.toString().getBytes(StandardCharsets.UTF_8));
        }
        fileOutputStream.close();

        JOptionPane.showMessageDialog(
            this.getRootPane().getParent(),
            "The data has been exported successfully as CSV to location:\n" + file.getCanonicalPath(),
            "Export successful...",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}
