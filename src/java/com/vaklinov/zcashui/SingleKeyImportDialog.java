/************************************************************************************************
 *  _________          _     ____          _           __        __    _ _      _   _   _ ___
 * |__  / ___|__ _ ___| |__ / ___|_      _(_)_ __   __ \ \      / /_ _| | | ___| |_| | | |_ _|
 *   / / |   / _` / __| '_ \\___ \ \ /\ / / | '_ \ / _` \ \ /\ / / _` | | |/ _ \ __| | | || |
 *  / /| |__| (_| \__ \ | | |___) \ V  V /| | | | | (_| |\ V  V / (_| | | |  __/ |_| |_| || |
 * /____\____\__,_|___/_| |_|____/ \_/\_/ |_|_| |_|\__, | \_/\_/ \__,_|_|_|\___|\__|\___/|___|
 *                                                 |___/
 *
 * Copyright (c) 2016 Ivan Vaklinov <ivan@vaklinov.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 **********************************************************************************/
package com.vaklinov.zcashui;


import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;


/**
 * Dialog to enter a single private key to import
 *
 * @author Ivan Vaklinov <ivan@vaklinov.com>
 */
public class SingleKeyImportDialog
	extends JDialog
{
	private JTextField keyField;
	private JProgressBar progress;
	
	private ZCashClientCaller caller;
	
	private JButton okButton;
	private JButton cancelButon;
		
	public SingleKeyImportDialog(JFrame parent, ZCashClientCaller caller)
	{
		super(parent);
		this.caller = caller;
		
		this.setTitle("Enter private key...");
	    this.setLocation(parent.getLocation().x + 50, parent.getLocation().y + 50);
		this.setModal(true);
		this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		
		JPanel controlsPanel = new JPanel();
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
				"<html><span style=\"font-weight:bold\">" +
				"Warning:</span> Private key import is a slow operation that " +
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
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
		okButton = new JButton("Import");
		buttonPanel.add(okButton);
		buttonPanel.add(new JLabel("   "));
		cancelButon = new JButton("Cancel");
		buttonPanel.add(cancelButon);
		this.getContentPane().add(buttonPanel, BorderLayout.SOUTH);

		okButton.addActionListener(e -> SingleKeyImportDialog.this.processOK());
		
		cancelButon.addActionListener(e -> {
            SingleKeyImportDialog.this.setVisible(false);
            SingleKeyImportDialog.this.dispose();
        });
		
		this.setSize(740, 210);
		this.validate();
		this.repaint();
	}
	
	
	protected void processOK()
	{
		final String key = SingleKeyImportDialog.this.keyField.getText();
		
		if ((key == null) || (key.trim().length() <= 0))
		{
			JOptionPane.showMessageDialog(
				SingleKeyImportDialog.this.getParent(), 
				"The key is empty. Please enter it into the text field.", "Empty...", 
				JOptionPane.ERROR_MESSAGE);
			return;
		}
				
		// Start import
		this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		this.progress.setIndeterminate(true);
		this.progress.setValue(1);
			
		this.okButton.setEnabled(false);
		this.cancelButon.setEnabled(false);
		
		SingleKeyImportDialog.this.keyField.setEditable(false);
			
		new Thread(() -> {
            try
            {
                SingleKeyImportDialog.this.caller.importPrivateKey(key);

                JOptionPane.showMessageDialog(
                        SingleKeyImportDialog.this,
                        "The private key:\n" +
                        key + "\n" +
                        "has been imported successfully.",
                        "Private key imported successfully...",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e)
            {
                e.printStackTrace();

                JOptionPane.showMessageDialog(
                    SingleKeyImportDialog.this.getRootPane().getParent(),
                    "An error occurred when importing private key. Error message is:\n" +
                    e.getClass().getName() + ":\n" + e.getMessage() + "\n\n" +
                    "Please ensure that hushd is running and the key is in the correct \n" +
                    "form. You may try again later...\n",
                    "Error in importing private key", JOptionPane.ERROR_MESSAGE);
            } finally
            {
                SingleKeyImportDialog.this.setVisible(false);
                SingleKeyImportDialog.this.dispose();
            }
        }).start();
	}
}
