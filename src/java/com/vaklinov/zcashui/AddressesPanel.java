/************************************************************************************************
 *  _________          _     ____          _           __        __    _ _      _   _   _ ___
 * |__  / ___|__ _ ___| |__ / ___|_      _(_)_ __   __ \ \      / /_ _| | | ___| |_| | | |_ _|
 *   / / |   / _` / __| '_ \\___ \ \ /\ / / | '_ \ / _` \ \ /\ / / _` | | |/ _ \ __| | | || |
 *  / /| |__| (_| \__ \ | | |___) \ V  V /| | | | | (_| |\ V  V / (_| | | |  __/ |_| |_| || |
 * /____\____\__,_|___/_| |_|____/ \_/\_/ |_|_| |_|\__, | \_/\_/ \__,_|_|_|\___|\__|\___/|___|
 *                                                 |___/
 *
 * Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
 * Copyright (c) 2018 The Hush Developers <contact@myhush.org>
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
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.border.EtchedBorder;

import com.vaklinov.zcashui.OSUtil.OS_TYPE;
import com.vaklinov.zcashui.ZCashClientCaller.WalletCallException;

/**
 * Addresses panel - shows T/Z addresses and their balnces.
 *
 * @author Ivan Vaklinov <ivan@vaklinov.com>
 */
public class AddressesPanel
	extends WalletTabPanel
{
	private JFrame parentFrame;
	private ZCashClientCaller clientCaller;
	private StatusUpdateErrorReporter errorReporter;

	private JTable addressBalanceTable   = null;
	private JScrollPane addressBalanceTablePane  = null;
	
	String[][] lastAddressBalanceData = null;
	
	private DataGatheringThread<String[][]> balanceGatheringThread = null;
	
	private long lastInteractiveRefresh;

		// Table of validated addresses with their validation result. An invalid or watch-only address should not be shown
	// and should be remembered as invalid here
	private Map<String, Boolean> validationMap = new HashMap<String, Boolean>();


	public AddressesPanel(JFrame parentFrame, ZCashClientCaller clientCaller, StatusUpdateErrorReporter errorReporter)
		throws IOException, InterruptedException, WalletCallException
	{
		this.parentFrame = parentFrame;
		this.clientCaller = clientCaller;
		this.errorReporter = errorReporter;
		
		this.lastInteractiveRefresh = System.currentTimeMillis();

		// Build content
		JPanel addressesPanel = this;
		addressesPanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
		addressesPanel.setLayout(new BorderLayout(0, 0));
	
		// Build panel of buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 3));
		buttonPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		
		JButton newTAddressButton = new JButton("New T (Transparent) address");
		buttonPanel.add(newTAddressButton);
		JButton newZAddressButton = new JButton("New Z (Private) address");
		buttonPanel.add(newZAddressButton);
		buttonPanel.add(new JLabel("           "));
		JButton refreshButton = new JButton("Refresh");
		buttonPanel.add(refreshButton);
		
		addressesPanel.add(buttonPanel, BorderLayout.SOUTH);

		// Table of addresses
		lastAddressBalanceData = getAddressBalanceDataFromWallet();
		addressesPanel.add(addressBalanceTablePane = new JScrollPane(
							   addressBalanceTable = this.createAddressBalanceTable(lastAddressBalanceData)),
						   BorderLayout.CENTER);
		
		JPanel warningPanel = new JPanel();
		warningPanel.setLayout(new BorderLayout(3, 3));
		warningPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
		JLabel warningL = new JLabel(
				"<html><span style=\"font-size:8px;\">" +
				"* If the balance of an address is flagged as not confirmed, the address is currently taking " +
				"part in a transaction. The shown balance then is the expected value it will have when " +
				"the transaction is confirmed. " +
				"The average confirmation time is 2.5 min." +
				"</span>");
		warningPanel.add(warningL, BorderLayout.NORTH);
		addressesPanel.add(warningPanel, BorderLayout.NORTH);
		
		// Thread and timer to update the address/balance table
		this.balanceGatheringThread = new DataGatheringThread<>(
				() -> {
                    long start = System.currentTimeMillis();
                    String[][] data = AddressesPanel.this.getAddressBalanceDataFromWallet();
                    long end = System.currentTimeMillis();
                    System.out.println("Gathering of address/balance table data done in " + (end - start) + "ms.");

                    return data;
                },
				this.errorReporter, 25000
		);
		this.threads.add(this.balanceGatheringThread);
		
		ActionListener alBalances = actionEvent -> {
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
		refreshButton.addActionListener(e -> {
            Cursor oldCursor = null;
            try {
                // TODO: dummy progress bar ... maybe
                oldCursor = AddressesPanel.this.getCursor();
                AddressesPanel.this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                AddressesPanel.this.updateWalletAddressBalanceTableInteractive();

                AddressesPanel.this.setCursor(oldCursor);
            } catch (Exception ex) {
                if (oldCursor != null) {
                    AddressesPanel.this.setCursor(oldCursor);
                }

                ex.printStackTrace();
                AddressesPanel.this.errorReporter.reportError(ex, false);
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

	private void createNewAddress(boolean isZAddress)
	{
		try
		{
			// Check for encrypted wallet
			final boolean bEncryptedWallet = this.clientCaller.isWalletEncrypted();
			if (bEncryptedWallet && isZAddress)
			{
				PasswordDialog pd = new PasswordDialog((JFrame)(this.getRootPane().getParent()));
				pd.setVisible(true);
				
				if (!pd.isOKPressed())
				{
					return;
				}
				
				this.clientCaller.unlockWallet(pd.getPassword());
			}

			String address = this.clientCaller.createNewAddress(isZAddress);
			
			// Lock the wallet again 
			if (bEncryptedWallet && isZAddress)
			{
				this.clientCaller.lockWallet();
			}
						
			JOptionPane.showMessageDialog(
				this.getRootPane().getParent(), 
				"A new " + (isZAddress ? "Z (Private)" : "T (Transparent)") 
				+ " address has been created cuccessfully:\n" + address, 
				"Address created", JOptionPane.INFORMATION_MESSAGE);
			
			this.updateWalletAddressBalanceTableInteractive();
		} catch (Exception e)
		{
			e.printStackTrace();			
			AddressesPanel.this.errorReporter.reportError(e, false);
		}
	}
	
	// Interactive and non-interactive are mutually exclusive
	private synchronized void updateWalletAddressBalanceTableInteractive()
		throws WalletCallException, IOException, InterruptedException
	{
		this.lastInteractiveRefresh = System.currentTimeMillis();
		
		String[][] newAddressBalanceData = this.getAddressBalanceDataFromWallet();

		if (Util.arraysAreDifferent(lastAddressBalanceData, newAddressBalanceData))
		{
			System.out.println("Updating table of addresses/balances I...");
			this.remove(addressBalanceTablePane);
			this.add(addressBalanceTablePane = new JScrollPane(
						 addressBalanceTable = this.createAddressBalanceTable(newAddressBalanceData)),
					 BorderLayout.CENTER);
			lastAddressBalanceData = newAddressBalanceData;

			this.validate();
			this.repaint();
		}
	}
	
	
	// Interactive and non-interactive are mutually exclusive
	private synchronized void updateWalletAddressBalanceTableAutomated()
		throws WalletCallException, IOException, InterruptedException
	{
		// Make sure it is > 1 min since the last interactive refresh
		if ((System.currentTimeMillis() - lastInteractiveRefresh) < (60 * 1000))
		{
			return;
		}
		
		String[][] newAddressBalanceData = this.balanceGatheringThread.getLastData();
		
		if ((newAddressBalanceData != null) && 
			Util.arraysAreDifferent(lastAddressBalanceData, newAddressBalanceData))
		{
			System.out.println("Updating table of addresses/balances A...");
			this.remove(addressBalanceTablePane);
			this.add(addressBalanceTablePane = new JScrollPane(
						 addressBalanceTable = this.createAddressBalanceTable(newAddressBalanceData)),
				 BorderLayout.CENTER);
			lastAddressBalanceData = newAddressBalanceData;
			this.validate();
			this.repaint();
		}
	}


	private JTable createAddressBalanceTable(String rowData[][]) {
		String columnNames[] = { "Balance", "Confirmed?", "Address" };
		JTable table = new AddressTable(rowData, columnNames, this.clientCaller);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		table.getColumnModel().getColumn(0).setPreferredWidth(160);
		table.getColumnModel().getColumn(1).setPreferredWidth(140);
		table.getColumnModel().getColumn(2).setPreferredWidth(1000);

		return table;
	}


	private String[][] getAddressBalanceDataFromWallet()
		throws WalletCallException, IOException, InterruptedException
	{
		// Z Addresses - they are OK
		String[] zAddresses = clientCaller.getWalletZAddresses();
		
		// T Addresses listed with the list received by addr comamnd
		String[] tAddresses = this.clientCaller.getWalletAllPublicAddresses();
		Set<String> tStoredAddressSet = new HashSet<>();
		Collections.addAll(tStoredAddressSet, tAddresses);
		
		// T addresses with unspent outputs - just in case they are different
		String[] tAddressesWithUnspentOuts = this.clientCaller.getWalletPublicAddressesWithUnspentOutputs();
		Set<String> tAddressSetWithUnspentOuts = new HashSet<>();
		Collections.addAll(tAddressSetWithUnspentOuts, tAddressesWithUnspentOuts);
		
		// Combine all known T addresses
		Set<String> tAddressesCombined = new HashSet<>();
		tAddressesCombined.addAll(tStoredAddressSet);
		tAddressesCombined.addAll(tAddressSetWithUnspentOuts);
		
		String[][] addressBalances = new String[zAddresses.length + tAddressesCombined.size()][];
		
		// Format double numbers - else sometimes we get exponential notation 1E-4 ZEC
		DecimalFormat df = new DecimalFormat("########0.00######");
		
		String confirmed    = "\u2690";
		String notConfirmed = "\u2691";
		
		// Windows does not support the flag symbol (Windows 7 by default)
		// TODO: isolate OS-specific symbol codes in a separate class
		OS_TYPE os = OSUtil.getOSType();
		if (os == OS_TYPE.WINDOWS)
		{
			confirmed = " \u25B7";
			notConfirmed = " \u25B6";
		}
		
		int i = 0;

		for (String address : tAddressesCombined)
		{
			String addressToDisplay = address;
			// Make sure the current address is not watch-only or invalid
			if (!this.validationMap.containsKey(address))
			{
				boolean validationResult = this.clientCaller.isWatchOnlyOrInvalidAddress(address);
				this.validationMap.put(address, new Boolean(validationResult));	
				
				if (validationResult)
				{
					JOptionPane.showMessageDialog(
						this.parentFrame,
						"An invalid or watch-only address exists in the wallet:" + "\n" +
						address + "\n\n" +
						"The GUI wallet software cannot operate properly with addresses that are invalid or\n" +
						"exist in the wallet as watch-only addresses. Do NOT use this address as a destination\n" +
						"address for payment operations!",
						"Error: invalid or watch-only address exists!",
						JOptionPane.ERROR_MESSAGE);
				}
			}
			
			boolean watchOnlyOrInvalid = this.validationMap.get(address).booleanValue();
			if (watchOnlyOrInvalid)
			{
				System.out.println("The following address is invalid or a watch-only address: {0}. It will not be displayed!" + address);
				addressToDisplay = "<INVALID OR WATCH-ONLY ADDRESS> !!!";
			}
			// End of check for invalid/watch only addresses

			String confirmedBalance = this.clientCaller.getBalanceForAddress(address);
			String unconfirmedBalance = this.clientCaller.getUnconfirmedBalanceForAddress(address);
			boolean isConfirmed =  (confirmedBalance.equals(unconfirmedBalance));			
			String balanceToShow = df.format(Double.valueOf(
				isConfirmed ? confirmedBalance : unconfirmedBalance));
			
			addressBalances[i++] = new String[] 
			{  
				balanceToShow,
				isConfirmed ? ("Yes " + confirmed) : ("No  " + notConfirmed),
				addressToDisplay
			};
		}
		
		for (String address : zAddresses)
		{
			String confirmedBalance = this.clientCaller.getBalanceForAddress(address);
			String unconfirmedBalance = this.clientCaller.getUnconfirmedBalanceForAddress(address);
			boolean isConfirmed =  (confirmedBalance.equals(unconfirmedBalance));
			String balanceToShow = df.format(Double.valueOf(
				isConfirmed ? confirmedBalance : unconfirmedBalance));
			
			addressBalances[i++] = new String[] 
			{  
				balanceToShow,
				isConfirmed ? ("Yes " + confirmed) : ("No  " + notConfirmed),
				address
			};
		}

		return addressBalances;
	}	

}
