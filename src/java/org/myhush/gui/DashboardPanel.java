// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import org.myhush.gui.environment.RuntimeEnvironment;
import org.myhush.gui.environment.system.DaemonInfo;
import org.myhush.gui.environment.system.DaemonInfoProvider;
import org.myhush.gui.environment.system.DaemonState;

import javax.swing.*;
import javax.swing.Timer;
import java.util.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;

class DashboardPanel extends WalletTabPanel {
    private final JFrame parentFrame;
    private final DaemonInfoProvider daemonInfoProvider;
    private final HushCommandLineBridge cliBridge;
    private final StatusUpdateErrorReporter errorReporter;

    private final JLabel networkAndBlockchainLabel;
    private final DataGatheringThread<HushCommandLineBridge.NetworkAndBlockchainInfo> netInfoGatheringThread;

    private Boolean walletIsEncrypted = null;
    private Integer blockchainPercentage = null;

    private String OSInfo = null;
    private final JLabel daemonStatusLabel;
    private final DataGatheringThread<DaemonInfo> daemonInfoGatheringThread;

    private final JLabel walletBalanceLabel;
    private final DataGatheringThread<HushCommandLineBridge.WalletBalance> walletBalanceGatheringThread;

    private JScrollPane transactionsTablePane;
    private String[][] lastTransactionsData;
    private final DataGatheringThread<String[][]> transactionGatheringThread;

    DashboardPanel(final JFrame parentFrame,
                   final DaemonInfoProvider daemonInfoProvider,
                   final HushCommandLineBridge cliBridge,
                   final StatusUpdateErrorReporter errorReporter
    ) throws IOException, InterruptedException, HushCommandLineBridge.WalletCallException {
        this.parentFrame = parentFrame;
        this.daemonInfoProvider = daemonInfoProvider;
        this.cliBridge = cliBridge;
        this.errorReporter = errorReporter;

        // Build content
        final JPanel dashboard = this;
        dashboard.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        dashboard.setLayout(new BorderLayout(0, 0));

        // Upper panel with wallet balance
        final JPanel balanceStatusPanel = new JPanel();
        // Use border layout to have balances to the left
        balanceStatusPanel.setLayout(new BorderLayout(3, 3));
        //balanceStatusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));

        final JPanel tempPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 13, 3));
        final JLabel logoLabel = new JLabel(new ImageIcon(this.getClass().getClassLoader().getResource("images/hush-logo-sm.png")));
        tempPanel.add(logoLabel);
        //tempPanel.add(new JLabel(" "));
        final JLabel hushLabel = new JLabel("HUSH Wallet     ");
        hushLabel.setFont(new Font("Helvetica", Font.BOLD | Font.ITALIC, 32));
        tempPanel.add(hushLabel);
        //tempPanel.setToolTipText("Powered by Hush\u00AE");
        balanceStatusPanel.add(tempPanel, BorderLayout.WEST);

        final JLabel transactionHeadingLabel = new JLabel("<html><span style=\"font-size:23px\"><br/></span>Transactions:</html>");
        transactionHeadingLabel.setFont(new Font("Helvetica", Font.BOLD, 19));
        balanceStatusPanel.add(transactionHeadingLabel, BorderLayout.CENTER);

        final PresentationPanel walletBalancePanel = new PresentationPanel();
        walletBalancePanel.add(walletBalanceLabel = new JLabel());
        balanceStatusPanel.add(walletBalancePanel, BorderLayout.EAST);

        dashboard.add(balanceStatusPanel, BorderLayout.NORTH);

        // Table of transactions
        lastTransactionsData = getTransactionsDataFromWallet();
        dashboard.add(transactionsTablePane = new JScrollPane(this.createTransactionsTable(lastTransactionsData)), BorderLayout.CENTER);

        // Lower panel with installation status
        final JPanel installationStatusPanel = new JPanel();
        installationStatusPanel.setLayout(new BorderLayout(3, 3));
        //installationStatusPanel.setBorder(BorderFactory.createEtchedBorder(EtchedBorder.LOWERED));
        final PresentationPanel daemonStatusPanel = new PresentationPanel();
        daemonStatusPanel.add(daemonStatusLabel = new JLabel());
        installationStatusPanel.add(daemonStatusPanel, BorderLayout.WEST);

        final PresentationPanel networkAndBlockchainPanel = new PresentationPanel();
        networkAndBlockchainPanel.add(networkAndBlockchainLabel = new JLabel());
        installationStatusPanel.add(networkAndBlockchainPanel, BorderLayout.EAST);

        dashboard.add(installationStatusPanel, BorderLayout.SOUTH);

        // Thread and timer to update the daemon status
        this.daemonInfoGatheringThread = new DataGatheringThread<>(
            () -> {
                final long start = System.currentTimeMillis();
                final DaemonInfo daemonInfo = DashboardPanel.this.daemonInfoProvider.getDaemonInfo();
                final long end = System.currentTimeMillis();
                System.out.println("Gathering of dashboard daemon status data done in " + (end - start) + "ms.");
                return daemonInfo;
            },
            this.errorReporter, 2000, true
        );
        this.threads.add(this.daemonInfoGatheringThread);

        final ActionListener alDeamonStatus = actionEvent -> {
            try {
                DashboardPanel.this.updateDaemonStatusLabel();
            } catch (final Exception e) {
                e.printStackTrace();
                DashboardPanel.this.errorReporter.reportError(e);
            }
        };
        final Timer timer = new Timer(1000, alDeamonStatus);
        timer.start();
        this.timers.add(timer);

        // Thread and timer to update the wallet balance
        this.walletBalanceGatheringThread = new DataGatheringThread<>(
            () -> {
                final long start = System.currentTimeMillis();
                final HushCommandLineBridge.WalletBalance balance = DashboardPanel.this.cliBridge.getWalletInfo();
                final long end = System.currentTimeMillis();

                // TODO: move this call to a dedicated one-off gathering thread - this is the wrong place
                // it works but a better design is needed.
                if (DashboardPanel.this.walletIsEncrypted == null) {
                    DashboardPanel.this.walletIsEncrypted = DashboardPanel.this.cliBridge.isWalletEncrypted();
                }
                System.out.println("Gathering of dashboard wallet balance data done in " + (end - start) + "ms.");
                return balance;
            },
            this.errorReporter, 8000, true
        );
        this.threads.add(this.walletBalanceGatheringThread);

        final ActionListener alWalletBalance = actionEvent -> {
            try {
                DashboardPanel.this.updateWalletStatusLabel();
            } catch (final Exception e) {
                e.printStackTrace();
                DashboardPanel.this.errorReporter.reportError(e);
            }
        };
        final Timer walletBalanceTimer = new Timer(2000, alWalletBalance);
        walletBalanceTimer.setInitialDelay(1000);
        walletBalanceTimer.start();
        this.timers.add(walletBalanceTimer);

        // Thread and timer to update the transactions table
        this.transactionGatheringThread = new DataGatheringThread<>(
            () -> {
                final long start = System.currentTimeMillis();
                final String[][] data = DashboardPanel.this.getTransactionsDataFromWallet();
                final long end = System.currentTimeMillis();
                System.out.println("Gathering of dashboard wallet transactions table data done in " + (end - start) + "ms.");
                return data;
            },
            this.errorReporter, 25000
        );
        this.threads.add(this.transactionGatheringThread);

        final ActionListener alTransactions = actionEvent -> {
            try {
                DashboardPanel.this.updateWalletTransactionsTable();
            } catch (final Exception e) {
                e.printStackTrace();
                DashboardPanel.this.errorReporter.reportError(e);
            }
        };
        final Timer updateTransactionsTimer = new Timer(5000, alTransactions);
        updateTransactionsTimer.start();
        this.timers.add(updateTransactionsTimer);

        // Thread and timer to update the network and blockchain details
        this.netInfoGatheringThread = new DataGatheringThread<>(
            () -> {
                final long start = System.currentTimeMillis();
                final HushCommandLineBridge.NetworkAndBlockchainInfo data = DashboardPanel.this.cliBridge.getNetworkAndBlockchainInfo();
                final long end = System.currentTimeMillis();
                System.out.println("Gathering of network and blockchain info data done in " + (end - start) + "ms.");
                return data;
            },
            this.errorReporter, 10000, true
        );
        this.threads.add(this.netInfoGatheringThread);

        final ActionListener alNetAndBlockchain = actionEvent -> {
            try {
                DashboardPanel.this.updateNetworkAndBlockchainLabel();
            } catch (final Exception e) {
                e.printStackTrace();
                DashboardPanel.this.errorReporter.reportError(e);
            }
        };
        final Timer netAndBlockchainTimer = new Timer(5000, alNetAndBlockchain);
        netAndBlockchainTimer.setInitialDelay(1000);
        netAndBlockchainTimer.start();
        this.timers.add(netAndBlockchainTimer);
    }

    // May be null!
    public Integer getBlockchainPercentage() {
        return this.blockchainPercentage;
    }

    private void updateDaemonStatusLabel() throws IOException, InterruptedException {
        final DaemonInfo daemonInfo = this.daemonInfoGatheringThread.getLastData();

        // It is possible there has been no gathering initially
        if (daemonInfo == null) {
            return;
        }

        // If the virtual size/CPU are 0 - do not show them
        final String virtual = (daemonInfo.virtualSizeMB > 0) ? ", Virtual: " + daemonInfo.virtualSizeMB + " MB" : "";
        final String cpuPercentage = (daemonInfo.cpuPercentage > 0) ? ", CPU: " + daemonInfo.cpuPercentage + "%" : "";

        final String daemonStatus;
        final String runtimeInfo;
        if (daemonInfo.status == DaemonState.RUNNING) {
            daemonStatus = "<span style=\"color:green;font-weight:bold\">RUNNING</span>";
            runtimeInfo = "<span style=\"font-size:8px\">" +
                          "Resident: " + daemonInfo.residentSizeMB + " MB" + virtual + cpuPercentage +
                          "</span>";
        } else {
            daemonStatus = "<span style=\"color:red;font-weight:bold\">NOT RUNNING</span>";
            runtimeInfo = "";
        }

        final File walletDAT = new File(App.PATH_PROVIDER.getBlockchainDirectory(), "wallet.dat");

        if (this.OSInfo == null) {
            this.OSInfo = RuntimeEnvironment.getSystemInfo();
        }

        // TODO: Use a one-off data gathering thread - better design
        final String walletEncryption = (this.walletIsEncrypted == null) ? "" :
            "<span style=\"font-size:8px\"> (" + (this.walletIsEncrypted ? "" : "not ") + "encrypted)</span>";

        this.daemonStatusLabel.setText(
            "<html>" +
            "<span style=\"font-weight:bold;color:#303030\">hushd</span> status: " +daemonStatus + ",  " + runtimeInfo + "<br/>" +
            "Wallet: <span style=\"font-weight:bold;color:#303030\">" + walletDAT.getCanonicalPath() + "</span>" + walletEncryption + "<br/>" +
            "<span style=\"font-size:3px\"><br/></span>" + // BRX-TODO: ??? tiny newline?
            "<span style=\"font-size:8px\">" +
            "Installation: " + App.PATH_PROVIDER.getProgramDirectory().getCanonicalPath() + ", " +
            "Blockchain: " + App.PATH_PROVIDER.getBlockchainDirectory().getCanonicalPath() + "<br/>" +
            "System: " + this.OSInfo + "</span></html>"
        );
    }

    private void updateNetworkAndBlockchainLabel() {
        final HushCommandLineBridge.NetworkAndBlockchainInfo info = this.netInfoGatheringThread.getLastData();

        // It is possible there has been no gathering initially
        if (info == null) {
            return;
        }
        final Date startDate = new Date("18 Nov 2016 01:53:31 GMT");
        final Date nowDate = new Date(System.currentTimeMillis());

        final long fullTime = nowDate.getTime() - startDate.getTime();
        final long remainingTime = nowDate.getTime() - info.lastBlockDate.getTime();

        if (remainingTime > 20 * 60 * 1000) // After 20 min we report 100% anyway
        {
            double dPercentage = 100d - (((double) remainingTime / (double) fullTime) * 100d);
            if (dPercentage < 0) {
                dPercentage = 0;
            } else if (dPercentage > 100d) {
                dPercentage = 100d;
            }
            // Also set a member that may be queried
            this.blockchainPercentage = (int) dPercentage;
        } else {
            this.blockchainPercentage = 100;
        }

        final String percentage =
            blockchainPercentage == 100 ? "100" : new DecimalFormat("##0.##").format(this.blockchainPercentage);

        // Just in case early on the call returns some junk date
        if (info.lastBlockDate.before(startDate)) {
            // TODO: write log that we fix minimum date! - this condition should not occur
            info.lastBlockDate = startDate;
        }

        final String connections = App.SPECIAL_CHARACTER_PROVIDER.getConnectionSymbol();
        final String tickSymbol = App.SPECIAL_CHARACTER_PROVIDER.getTickSymbol();

        final String tick = percentage.equals("100") ?
            "<span style=\"font-weight:bold;font-size:12px;color:green\"> " + tickSymbol + "</span>" : "";

        final String netColor;
        if (info.numConnections > 6) {
            netColor = "green";
        } else if (info.numConnections > 2) {
            netColor = "black";
        } else if (info.numConnections > 0) {
            netColor = "#cc3300";
        } else {
            netColor = "red";
        }

        this.networkAndBlockchainLabel.setText(
            "<html>Blockchain synchronized: <span style=\"font-weight:bold\">" + percentage + "% </span> " + tick + " <br/>" +
            "Up to: <span style=\"font-size:8px;font-weight:bold\">" + info.lastBlockDate.toLocaleString() + "</span>  <br/> " +
            "<span style=\"font-size:1px\"><br/></span>" +
            "Network: <span style=\"font-weight:bold\">" + info.numConnections + " connections</span>" +
            "<span style=\"font-size:16px;color:" + netColor + "\"> " + connections + "</span>"
        );
    }

    private void updateWalletStatusLabel() {
        final HushCommandLineBridge.WalletBalance balance = this.walletBalanceGatheringThread.getLastData();

        // It is possible there has been no gathering initially
        if (balance == null) {
            return;
        }

        // Format double numbers - else sometimes we get exponential notation 1E-4 ZEC
        final DecimalFormat df = new DecimalFormat("########0.00######");

        final String transparentBalance = df.format(balance.transparentBalance);
        final String privateBalance = df.format(balance.privateBalance);
        final String totalBalance = df.format(balance.totalBalance);

        final String transparentUCBalance = df.format(balance.transparentUnconfirmedBalance);
        final String privateUCBalance = df.format(balance.privateUnconfirmedBalance);
        final String totalUCBalance = df.format(balance.totalUnconfirmedBalance);

        final String color1 = transparentBalance.equals(transparentUCBalance) ? "" : "color:#cc3300;";
        final String color2 = privateBalance.equals(privateUCBalance) ? "" : "color:#cc3300;";
        final String color3 = totalBalance.equals(totalUCBalance) ? "" : "color:#cc3300;";

        this.walletBalanceLabel.setText(
            "<html>" +
            "<span style=\"font-family:monospace;font-size:8.9px;" + color1 + "\">" +
                "Transparent balance: <span style=\"font-size:9px\">" + transparentUCBalance + " HUSH </span>" +
            "</span><br/> " +
            "<span style=\"font-family:monospace;font-size:8.9px;" + color2 + "\">" +
                "Private (Z) balance: <span style=\"font-weight:bold;font-size:9px\">" + privateUCBalance + " HUSH </span>" +
                    "</span><br/> " +
            "<span style=\"font-family:monospace;font-size:8.9px;" + color3 + "\">" +
                "Total (Z+T) balance: <span style=\"font-weight:bold;font-size:11.5px;\">" + totalUCBalance + " HUSH </span>" +
            "</span><br/>  </html>"
       );

        final String toolTip;
        if (transparentBalance.equals(transparentUCBalance) && !privateBalance.equals(privateUCBalance) && totalBalance.equals(totalUCBalance)) {
            toolTip = null;
        } else { // some balances don't line up
            toolTip = "<html>Unconfirmed (unspendable) balance is being shown due to an<br/>" +
                      "ongoing transaction! Actual confirmed (spendable) balance is:<br/>" +
                      "<span style=\"font-size:5px\"><br/></span>" +
                      "Transparent: " + transparentBalance + " HUSH<br/>" +
                      "Private ( Z ): <span style=\"font-weight:bold\">" + privateBalance + " HUSH</span><br/>" +
                      "Total ( Z+T ): <span style=\"font-weight:bold\">" + totalBalance + " HUSH</span></html>";
        }
        this.walletBalanceLabel.setToolTipText(toolTip);
    }

    private void updateWalletTransactionsTable() {
        final String[][] newTransactionsData = this.transactionGatheringThread.getLastData();

        // May be null - not even gathered once
        if (newTransactionsData == null) {
            return;
        }
        if (!Arrays.deepEquals(lastTransactionsData, newTransactionsData)) {
            System.out.println("Updating table of transactions...");
            this.remove(transactionsTablePane);
            this.add(transactionsTablePane = new JScrollPane(this.createTransactionsTable(newTransactionsData)), BorderLayout.CENTER);
        }
        lastTransactionsData = newTransactionsData;

        this.validate();
        this.repaint();
    }


    private JTable createTransactionsTable(String rowData[][]) {
        final String columnNames[] = { "Type", "Direction", "Confirmed?", "Amount", "Date", "Destination Address" };
        final JTable table = new TransactionTable(rowData, columnNames, this.parentFrame, this.cliBridge);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.getColumnModel().getColumn(0).setPreferredWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(145);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(210);
        table.getColumnModel().getColumn(4).setPreferredWidth(405);
        table.getColumnModel().getColumn(5).setPreferredWidth(800);
        return table;
    }


    private String[][] getTransactionsDataFromWallet()
            throws HushCommandLineBridge.WalletCallException, IOException, InterruptedException {
        // Get available public+private transactions and unify them.
        final String[][] publicTransactions = this.cliBridge.getWalletPublicTransactions();
        final String[][] zReceivedTransactions = this.cliBridge.getWalletZReceivedTransactions();

        final List<String[]> transactions = new ArrayList<>();
        transactions.addAll(Arrays.asList(publicTransactions));
        transactions.addAll(Arrays.asList(zReceivedTransactions));

        // Sort transactions by date
        Collections.sort(transactions, (a, b) -> {
            final Date aDate = new Date(
                a[4].equals("N/A") ? 0 : Long.valueOf(a[4]) * 1000L
            );
            final Date bDate = new Date(
                b[4].equals("N/A") ? 0 : Long.valueOf(b[4]) * 1000L
            );

            if (aDate.equals(bDate)) {
                return 0;
            } else {
                return bDate.compareTo(aDate);
            }
        });


        // Confirmation symbols
        final String confirmed = App.SPECIAL_CHARACTER_PROVIDER.getConfirmedBalanceSymbol();
        final String notConfirmed = App.SPECIAL_CHARACTER_PROVIDER.getUnconfirmedBalanceSymbol();

        final DecimalFormat decimalFormat = new DecimalFormat("########0.00######");

        // Change the direction and date etc. attributes for presentation purposes
        for (final String[] transaction : transactions) {
            // Direction
            switch (transaction[1]) {
                case "receive":
                    transaction[1] = "\u21E8 IN";
                    break;
                case "send":
                    transaction[1] = "\u21E6 OUT";
                    break;
                case "generate":
                    transaction[1] = "\u2692\u2699 MINED";
                    break;
                case "immature":
                    transaction[1] = "\u2696 Immature";
                    break;
            }

            // Date
            if (!transaction[4].equals("N/A")) {
                transaction[4] = new Date(Long.valueOf(transaction[4]) * 1000L).toLocaleString();
            }

            // Amount
            try {
                final double amount = Math.abs(Double.valueOf(transaction[3]));
                transaction[3] = decimalFormat.format(amount);
            } catch (final NumberFormatException e) {
                System.out.println("Error occurred while formatting amount: " + transaction[3] + " - " + e.getMessage() + "!");
            }

            // Confirmed?
            try {
                final boolean isConfirmed = !transaction[2].trim().equals("0");
                transaction[2] = isConfirmed ? ("Yes " + confirmed) : ("No  " + notConfirmed);
            } catch (final NumberFormatException e) {
                System.out.println("Error occurred while formatting confirmations: " + transaction[2] + " - " + e.getMessage() + "!");
            }
        }
        return (String[][])transactions.toArray();
    }
}
