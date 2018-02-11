// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

class AboutDialog extends JDialog {
    AboutDialog(final JFrame parent) {
        this.setTitle("About...");
        this.setSize(600, 450);
        this.setLocation(100, 100);
        this.setLocationRelativeTo(parent);
        this.setModal(true);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        final JTabbedPane tabs = new JTabbedPane();
        tabs.add("About", getCopyrightPanel());
        tabs.add("License", getLicensePanel());

        this.getContentPane().setLayout(new BorderLayout(0, 0));
        this.getContentPane().add(tabs, BorderLayout.NORTH);

        final JPanel closePanel = new JPanel();
        closePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
        JButton closeButon = new JButton("Close");
        closePanel.add(closeButon);
        this.getContentPane().add(closePanel, BorderLayout.SOUTH);

        closeButon.addActionListener(actionEvent -> {
            AboutDialog.this.setVisible(false);
            AboutDialog.this.dispose();
        });
    }

    private JPanel getCopyrightPanel() {
        final JPanel copyrightPanel = new JPanel();
        copyrightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        copyrightPanel.setLayout(new BorderLayout(3, 3));
        final JLabel copyrightLabel = new JLabel();
        copyrightLabel.setText(
            "<html><body><span style=\"font-weight:bold\">HUSH Swing Wallet UI</span><br/><br/>" +
            "Copyright: The Hush Developers &lt;contact@myhush.org&gt;<br/><br/>This program is intended to make it easy " +
            "to work with the HUSH client tools by providing a Graphical User Interface (GUI) that acts as a wrapper " +
            "and presents the information in a user-friendly manner.<br/><br/>Acknowledgements: This program includes " +
            "software for JSON processing (https://github.com/ralfstx/minimal-json) that is Copyright (c) 2015, " +
            "2016 EclipseSource.<br/><br/></body></html>"
        );
        copyrightLabel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        copyrightPanel.add(copyrightLabel, BorderLayout.NORTH);

        final JPanel PD = new JPanel();
        PD.setLayout(new BorderLayout(3, 3));
        PD.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        final JLabel l1 = new JLabel(
            "<html><body><span style=\"font-weight:bold\">Donations accepted:</span> This HUSH GUI wallet has " +
            "been brought to you through the efforts of community volunteers. If you find it useful please consider " +
            "making a donation for its further development. Donations of <span style=\"font-weight:bold\">any size" +
            "</span> are accepted to the following HUSH address:<br/></body></html>"
        );
        PD.add(l1, BorderLayout.NORTH);

        final JPanel PD2 = new JPanel();
        PD2.setLayout(new BorderLayout(3, 3));
        final JTextArea tar = new JTextArea();
        tar.setEditable(false);
        tar.setLineWrap(true);
        tar.setText(Constants.HUSH_DONATION_ADDRESS);
        PD2.add(tar, BorderLayout.CENTER);

        final JPanel PD3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        final JButton jb1 = new JButton(
            "<html><body><span style=\"font-size:8px;font-weight:bold\">Copy address<br/>to clipboard</span></html></body>"
        );
        PD3.add(jb1);
        jb1.addActionListener(actionEvent -> {
            final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(tar.getText()), null);
        });
        PD2.add(PD3, BorderLayout.EAST);
        PD.add(PD2, BorderLayout.CENTER);
        copyrightPanel.add(PD, BorderLayout.CENTER);
        return copyrightPanel;
    }

    private JPanel getLicensePanel() {
        final JPanel licensePanel = new JPanel();
        licensePanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        licensePanel.setLayout(new BorderLayout(3, 3));
        final JLabel licenseLabel = new JLabel();
        licenseLabel.setText(
            "<html><body><pre>" +
            " Copyright (c) 2016-2017 Ivan Vaklinov &lt;ivan@vaklinov.com&gt; \n" +
            " Copyright (c) 2018 The Hush Developers &lt;contact@myhush.org&gt; \n" +
            "\n" +
            " Permission is hereby granted, free of charge, to any person obtaining a copy\n" +
            " of this software and associated documentation files (the \"Software\"), to deal\n" +
            " in the Software without restriction, including without limitation the rights\n" +
            " to use, copy, modify, merge, publish, distribute, sublicense, and/or sell\n" +
            " copies of the Software, and to permit persons to whom the Software is\n" +
            " furnished to do so, subject to the following conditions:\n" +
            " \n" +
            " The above copyright notice and this permission notice shall be included in\n" +
            " all copies or substantial portions of the Software.\n" +
            " \n" +
            " THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\n" +
            " IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\n" +
            " FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\n" +
            " AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\n" +
            " LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\n" +
            " OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN\n" +
            " THE SOFTWARE.\n" +
            "</pre></body></html>"
        );
        licenseLabel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        licensePanel.add(licenseLabel, BorderLayout.NORTH);
        return licensePanel;
    }
}
