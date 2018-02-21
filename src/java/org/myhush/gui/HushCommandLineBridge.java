// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import com.eclipsesource.json.*;
import org.myhush.gui.environment.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Stream;

public class HushCommandLineBridge {
    // BRX-TODO: Can be finalized by using Files.exists() below in constructor in place of `new File(...).exists()`
    private File hushcli;
    private File hushd;

    public HushCommandLineBridge(final File installDirectory) throws IOException {
        // Detect daemon and client tools installation
        hushcli = new File(installDirectory, RuntimeEnvironment.getHushCliFileName());
        if (!hushcli.exists()) {
            throw new IOException(
                    "The Hush installation directory \"" + installDirectory.getCanonicalPath() + "\" needs" +
                            "to contain the command line utilities `hushd` and `hush-cli`. `hush-cli` is missing!"
            );
        }

        hushd = new File(installDirectory, RuntimeEnvironment.getHushDaemonFileName());
        if (!hushd.exists()) {
            throw new IOException(
                    "The HUSH command line utility " + hushcli.getCanonicalPath() +
                            " was found, but `hushd` was not found!");
        }
    }

    public synchronized Process startDaemon() throws IOException {
        final String dataDirectory = App.PATH_PROVIDER.getBlockchainDirectory().getCanonicalPath();
        final String[] args = new String[]{
                hushd.getCanonicalPath(),
                wrapStringParameter(String.format("-datadir=%s", dataDirectory)),
                wrapStringParameter(String.format("-exportdir=%s", dataDirectory))
        };
        return new CommandExecutor(args).startChildProcess();
    }

    private CommandExecutor getCommandLineExecutor(final String[] args) throws IOException {
        final String dataDirectory = App.PATH_PROVIDER.getBlockchainDirectory().getCanonicalPath();
        final String[] processArgs = new String[] {
                hushcli.getCanonicalPath(),
                wrapStringParameter(String.format("-datadir=%s", dataDirectory)),
        };
        final String[] runnableArgs = Arrays.copyOf(processArgs, processArgs.length + args.length);
        System.arraycopy(args, 0, runnableArgs, processArgs.length, args.length);
        return new CommandExecutor(runnableArgs);
    }

    public /*synchronized*/ void stopDaemon() throws IOException, InterruptedException {
        final String result = getCommandLineExecutor(new String[]{ "stop" }).execute();
        System.out.println("Stop command issued: " + result);
    }

    public synchronized JsonObject getDaemonRawRuntimeInfo()
            throws IOException, InterruptedException, DaemonUnavailableException {
        final String info = getCommandLineExecutor(new String[]{ "getinfo" }).execute();

        if (info.trim().toLowerCase(Locale.ROOT).startsWith("error: couldn't connect to server")) {
            throw new DaemonUnavailableException(info.trim());
        }

        if (info.trim().toLowerCase(Locale.ROOT).startsWith("error: ")) {
            final String errorInfo = info.substring(7);

            try {
                return Json.parse(errorInfo).asObject();
            } catch (final ParseException e) {
                System.out.println("unexpected daemon info: " + errorInfo);
                throw new IOException(e);
            }
        } else if (info.trim().toLowerCase(Locale.ROOT).startsWith("error code:")) {
            return jsonifyErrorMessage(info);
        } else {
            try {
                return Json.parse(info).asObject();
            } catch (final ParseException e) {
                System.out.println("unexpected daemon info: " + info);
                throw new IOException(e);
            }
        }
    }

    public synchronized WalletBalance getWalletInfo() throws WalletCallException, IOException, InterruptedException {
        final WalletBalance balance = new WalletBalance();

        // call `hush-cli z_gettotalbalance [1]` to get confirmed balances (1 is default, included)
        final JsonObject confirmedBalances = this.executeCommandAndGetJsonObject("z_gettotalbalance", null);

        balance.transparentBalance = Double.valueOf(confirmedBalances.getString("transparent", "-1"));
        balance.privateBalance = Double.valueOf(confirmedBalances.getString("private", "-1"));
        balance.totalBalance = Double.valueOf(confirmedBalances.getString("total", "-1"));

        // call `hush-cli z_gettotalbalance 0` to get balances with 0 or more confirmations, ie. all
        final JsonObject totalBalances = this.executeCommandAndGetJsonObject("z_gettotalbalance", "0");

        balance.transparentUnconfirmedBalance = Double.valueOf(totalBalances.getString("transparent", "-1"));
        balance.privateUnconfirmedBalance = Double.valueOf(totalBalances.getString("private", "-1"));
        balance.totalUnconfirmedBalance = Double.valueOf(totalBalances.getString("total", "-1"));

        return balance;
    }

    public synchronized String[][] getWalletPublicTransactions()
            throws WalletCallException, IOException, InterruptedException {
        final String notListed = App.SPECIAL_CHARACTER_PROVIDER.getUnlistedAddressSymbol();
        final JsonArray jsonTransactions = executeCommandAndGetJsonArray(
            "listtransactions", wrapStringParameter(""), "100"
        );
        final String strTransactions[][] = new String[jsonTransactions.size()][];
        for (int i = 0; i < jsonTransactions.size(); i++) {
            strTransactions[i] = new String[7];
            JsonObject trans = jsonTransactions.get(i).asObject();

            // Needs to be the same as in getWalletZReceivedTransactions()
            // TODO: some day refactor to use object containers
            strTransactions[i][0] = "\u2606T (Public)";
            strTransactions[i][1] = trans.getString("category", "ERROR!");
            strTransactions[i][2] = trans.get("confirmations").toString();
            strTransactions[i][3] = trans.get("amount").toString();
            strTransactions[i][4] = trans.get("time").toString();
            strTransactions[i][5] = trans.getString("address", notListed + " (Z Address not listed by wallet!)");
            strTransactions[i][6] = trans.get("txid").toString();
        }
        return strTransactions;
    }

    public synchronized String[] getWalletZAddresses() throws WalletCallException, IOException, InterruptedException {
        final JsonArray jsonAddresses = executeCommandAndGetJsonArray("z_listaddresses", null);
        final String strAddresses[] = new String[jsonAddresses.size()];
        for (int i = 0; i < jsonAddresses.size(); i++) {
            strAddresses[i] = jsonAddresses.get(i).asString();
        }
        return strAddresses;
    }

    public synchronized String[][] getWalletZReceivedTransactions()
            throws WalletCallException, IOException, InterruptedException {
        final String[] zAddresses = this.getWalletZAddresses();
        final List<String[]> zReceivedTransactions = new ArrayList<>();

        for (final String zAddress : zAddresses) {
            final JsonArray jsonTransactions = executeCommandAndGetJsonArray(
                "z_listreceivedbyaddress", wrapStringParameter(zAddress), "0"
            );
            for (int i = 0; i < jsonTransactions.size(); i++) {
                final String[] currentTransaction = new String[7];
                final JsonObject trans = jsonTransactions.get(i).asObject();

                String txID = trans.getString("txid", "ERROR!");
                // Needs to be the same as in getWalletPublicTransactions()
                // TODO: some day refactor to use object containers
                currentTransaction[0] = "\u2605Z (Private)";
                currentTransaction[1] = "receive";
                currentTransaction[2] = this.getWalletTransactionConfirmations(txID);
                currentTransaction[3] = trans.get("amount").toString();
                currentTransaction[4] = this.getWalletTransactionTime(txID); // TODO: minimize sub-calls
                currentTransaction[5] = zAddress;
                currentTransaction[6] = trans.get("txid").toString();

                zReceivedTransactions.add(currentTransaction);
            }
        }
        return zReceivedTransactions.toArray(new String[0][]);
    }

    // ./src/hush-cli listunspent only returns T addresses it seems
    public synchronized String[] getWalletPublicAddressesWithUnspentOutputs()
            throws WalletCallException, IOException, InterruptedException {
        final JsonArray jsonUnspentOutputs = executeCommandAndGetJsonArray("listunspent", "0");
        final Set<String> addresses = new HashSet<>();

        for (int i = 0; i < jsonUnspentOutputs.size(); i++) {
            final JsonObject outp = jsonUnspentOutputs.get(i).asObject();
            addresses.add(outp.getString("address", "ERROR!"));
        }
        return addresses.toArray(new String[0]);
    }

    // ./hush-cli listreceivedbyaddress 0 true
    public synchronized String[] getWalletAllPublicAddresses()
            throws WalletCallException, IOException, InterruptedException {
        final JsonArray jsonReceivedOutputs = executeCommandAndGetJsonArray("listreceivedbyaddress", "0", "true");
        final Set<String> addresses = new HashSet<>();

        for (int i = 0; i < jsonReceivedOutputs.size(); i++) {
            final JsonObject outp = jsonReceivedOutputs.get(i).asObject();
            addresses.add(outp.getString("address", "ERROR!"));
        }
        return addresses.toArray(new String[0]);
    }

    public synchronized Map<String, String> getRawTransactionDetails(final String txID)
            throws WalletCallException, IOException, InterruptedException {
        final JsonObject jsonTransaction = this.executeCommandAndGetJsonObject(
            "gettransaction", wrapStringParameter(txID)
        );
        final Map<String, String> map = new HashMap<>();

        for (final String name : jsonTransaction.names()) {
            this.decomposeJSONValue(name, jsonTransaction.get(name), map);
        }
        return map;
    }

    public synchronized String getMemoField(final String acc, final String txID)
            throws WalletCallException, IOException, InterruptedException {
        final JsonArray jsonTransactions = this.executeCommandAndGetJsonArray(
            "z_listreceivedbyaddress", wrapStringParameter(acc)
        );

        for (int i = 0; i < jsonTransactions.size(); i++) {
            if (jsonTransactions.get(i).asObject().getString("txid", "ERROR!").equals(txID)) {
                if (jsonTransactions.get(i).asObject().get("memo") == null) {
                    return null;
                }

                final String memoHex = jsonTransactions.get(i).asObject().getString("memo", "ERROR!");
                // Skip empty memos
                if (memoHex.startsWith("f60000")) {
                    return null;
                }

                final StringBuilder memoAscii = new StringBuilder();
                for (int j = 0; j < memoHex.length(); j += 2) {
                    final String str = memoHex.substring(j, j + 2);
                    if (!str.equals("00")) {// Zero bytes are empty
                        memoAscii.append((char) Integer.parseInt(str, 16));
                    }
                }
                return memoAscii.toString();
            }
        }
        return null;
    }

    public synchronized String getRawTransaction(final String txID)
            throws WalletCallException, IOException, InterruptedException {
        final JsonObject jsonTransaction = this.executeCommandAndGetJsonObject(
            "gettransaction", wrapStringParameter(txID)
        );
        return jsonTransaction.toString(WriterConfig.PRETTY_PRINT);
    }

    // return UNIX time as String
    private synchronized String getWalletTransactionTime(final String txID)
            throws WalletCallException, IOException, InterruptedException {
        final JsonObject jsonTransaction = this.executeCommandAndGetJsonObject(
            "gettransaction", wrapStringParameter(txID)
        );
        return String.valueOf(jsonTransaction.getLong("time", -1));
    }

    private synchronized String getWalletTransactionConfirmations(String txID)
            throws WalletCallException, IOException, InterruptedException {
        final JsonObject jsonTransaction = this.executeCommandAndGetJsonObject(
            "gettransaction", wrapStringParameter(txID)
        );
        return jsonTransaction.get("confirmations").toString();
    }

    // Checks if a certain T address is a watch-only address or is otherwise invalid.
    public synchronized boolean isWatchOnlyOrInvalidAddress(String address)
            throws WalletCallException, IOException, InterruptedException {
        final JsonObject response = this.executeCommandAndGetJsonValue(
            "validateaddress", wrapStringParameter(address)
        ).asObject();

        if (response.getBoolean("isvalid", false)) {
            return response.getBoolean("iswatchonly", true);
        }
        return true;
    }

    // Returns confirmed balance only!
    public synchronized String getBalanceForAddress(final String address)
            throws WalletCallException, IOException, InterruptedException {
        final JsonValue response = this.executeCommandAndGetJsonValue(
            "z_getbalance", wrapStringParameter(address)
        );
        return String.valueOf(response.toString());
    }

    public synchronized String getUnconfirmedBalanceForAddress(final String address)
            throws WalletCallException, IOException, InterruptedException {
        final JsonValue response = this.executeCommandAndGetJsonValue(
            "z_getbalance", wrapStringParameter(address), "0"
        );
        return String.valueOf(response.toString());
    }

    public synchronized String createNewAddress(final boolean isZAddress)
            throws WalletCallException, IOException, InterruptedException {
        return this.executeCommandAndGetSingleStringResponse((isZAddress ? "z_" : "") + "getnewaddress").trim();
    }

    // Returns OPID
    public synchronized String sendCash(
        final String from,
        final String to,
        final String amount,
        final String memo,
        final String transactionFee
    ) throws WalletCallException, IOException, InterruptedException {
        final StringBuilder hexMemo = new StringBuilder();
        for (final byte c : memo.getBytes(StandardCharsets.UTF_8)) {
            final String hexChar = Integer.toHexString((int) c);
            if (hexChar.length() < 2) {
                hexMemo.append("0");
            }
            hexMemo.append(hexChar);
        }

        final JsonObject toArgument = new JsonObject();
        toArgument.set("address", to);
        if (hexMemo.length() >= 2) {
            toArgument.set("memo", hexMemo.toString());
        }

        // The JSON Builder has a problem with double values that have no fractional part
        // it serializes them as integers that Hush does not accept. So we do a replacement
        // TODO: find a better/cleaner way to format the amount
        toArgument.set("amount", "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF");

        final JsonArray toMany = new JsonArray();
        toMany.add(toArgument);

        final String amountPattern = "\"amount\":\"\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\"";
        // Make sure our replacement hack never leads to a mess up
        final String toManyBeforeReplace = toMany.toString();
        final int firstIndex = toManyBeforeReplace.indexOf(amountPattern);
        final int lastIndex = toManyBeforeReplace.lastIndexOf(amountPattern);
        if ((firstIndex == -1) || (firstIndex != lastIndex)) {
            throw new WalletCallException("Error in forming z_sendmany command: " + toManyBeforeReplace);
        }

        DecimalFormatSymbols decSymbols = new DecimalFormatSymbols(Locale.ROOT);

        // Properly format teh transaction fee as a number
        final String preparedTxFee;
        if ((transactionFee == null) || (transactionFee.trim().length() == 0)) {
            preparedTxFee = "0.0001"; // Default value
        } else {
            preparedTxFee = new DecimalFormat("########0.00######", decSymbols).format(Double.valueOf(transactionFee));
        }

        // This replacement is a hack to make sure the JSON object amount has double format 0.00 etc.
        // TODO: find a better way to format the amount
        final String toManyArrayStr = toMany.toString().replace(
            amountPattern,
            "\"amount\":" + new DecimalFormat("########0.00######", decSymbols).format(Double.valueOf(amount))
        );

        // Safeguard to make sure the monetary amount does not differ after formatting
        final BigDecimal bdAmount = new BigDecimal(amount);
        final JsonArray toManyVerificationArr = Json.parse(toManyArrayStr).asArray();
        final BigDecimal bdFinalAmount = new BigDecimal(toManyVerificationArr.get(0).asObject().getDouble("amount", -1));
        final BigDecimal difference = bdAmount.subtract(bdFinalAmount).abs();
        if (difference.compareTo(new BigDecimal("0.000000015")) >= 0) {
            throw new WalletCallException(
                "Error in forming z_sendmany command: Amount differs after formatting: " + amount + " | " + toManyArrayStr
            );
        }

        final String[] sendCashParameters = new String[]{
                "z_sendmany",
                wrapStringParameter(from),
                wrapStringParameter(toManyArrayStr),
                "1",                                   // default min confirmations for the input transactions is 1
                preparedTxFee                          // transaction fee
        };

        System.out.println(
            "The following send command will be issued: " +
            sendCashParameters[0] + " " + sendCashParameters[1] + " " +
            sendCashParameters[2] + " " + sendCashParameters[3] + " " +
            sendCashParameters[4] + " " + sendCashParameters[5] + "."
        );

        // Create caller to send cash
        final String strResponse = getCommandLineExecutor(sendCashParameters).execute();

        if (strResponse.trim().toLowerCase(Locale.ROOT).startsWith("error:") ||
                    strResponse.trim().toLowerCase(Locale.ROOT).startsWith("error code:")) {
            throw new WalletCallException("Error response from wallet: " + strResponse);
        }

        System.out.println(
            "Sending cash with the following command: " +
            sendCashParameters[0] + " " + sendCashParameters[1] + " " +
            sendCashParameters[2] + " " + sendCashParameters[3] + " " +
            sendCashParameters[4] + " " + sendCashParameters[5] + "." +
            " Got result: [" + strResponse + "]"
        );
        return strResponse.trim();
    }

    public synchronized boolean isSendingOperationComplete(final String opID)
            throws WalletCallException, IOException, InterruptedException {
        final JsonArray response = this.executeCommandAndGetJsonArray(
            "z_getoperationstatus", wrapStringParameter("[\"" + opID + "\"]")
        );
        final JsonObject jsonStatus = response.get(0).asObject();
        final String status = jsonStatus.getString("status", "ERROR");

        System.out.println("Operation " + opID + " status is " + response + ".");

        if (status.equalsIgnoreCase("success") || status.equalsIgnoreCase("error") || status.equalsIgnoreCase("failed")) {
            return true;
        } else if (status.equalsIgnoreCase("executing") || status.equalsIgnoreCase("queued")) {
            return false;
        } else {
            throw new WalletCallException("Unexpected status response from wallet: " + response.toString());
        }
    }

    public synchronized boolean isCompletedOperationSuccessful(final String opID)
            throws WalletCallException, IOException, InterruptedException {
        final JsonArray response = this.executeCommandAndGetJsonArray(
            "z_getoperationstatus", wrapStringParameter("[\"" + opID + "\"]")
        );
        final JsonObject jsonStatus = response.get(0).asObject();
        final String status = jsonStatus.getString("status", "ERROR");

        System.out.println("Operation " + opID + " status is " + response + ".");

        if (status.equalsIgnoreCase("success")) {
            return true;
        } else if (status.equalsIgnoreCase("error") || status.equalsIgnoreCase("failed")) {
            return false;
        } else {
            throw new WalletCallException("Unexpected final operation status response from wallet: " + response.toString());
        }
    }

    // May only be called for already failed operations
    public synchronized String getOperationFinalErrorMessage(final String opID)
            throws WalletCallException, IOException, InterruptedException {
        final JsonArray response = this.executeCommandAndGetJsonArray(
            "z_getoperationstatus", wrapStringParameter("[\"" + opID + "\"]")
        );
        final JsonObject jsonStatus = response.get(0).asObject();
        final JsonObject jsonError = jsonStatus.get("error").asObject();
        return jsonError.getString("message", "ERROR!");
    }

    public synchronized NetworkAndBlockchainInfo getNetworkAndBlockchainInfo()
            throws WalletCallException, IOException, InterruptedException {
        final NetworkAndBlockchainInfo info = new NetworkAndBlockchainInfo();
        final String strNumCons = this.executeCommandAndGetSingleStringResponse("getconnectioncount");
        info.numConnections = Integer.valueOf(strNumCons.trim());

        final String strBlockCount = this.executeCommandAndGetSingleStringResponse("getblockcount");
        final String lastBlockHash = this.executeCommandAndGetSingleStringResponse("getblockhash", strBlockCount.trim());
        final JsonObject lastBlock = this.executeCommandAndGetJsonObject("getblock", wrapStringParameter(lastBlockHash.trim()));
        info.lastBlockDate = new Date(lastBlock.getLong("time", -1) * 1000L);

        return info;
    }

    public synchronized void lockWallet()
            throws WalletCallException, IOException, InterruptedException {
        final String response = this.executeCommandAndGetSingleStringResponse("walletlock");

        // Response is expected to be empty
        if (response.trim().length() > 0) {
            throw new WalletCallException("Unexpected response from wallet: " + response);
        }
    }

    // Unlocks the wallet for 5 minutes - meant to be followed shortly by lock!
    // TODO: tests with a password containing spaces
    public synchronized void unlockWallet(String password)
            throws WalletCallException, IOException, InterruptedException {
        final String response = this.executeCommandAndGetSingleStringResponse(
            "walletpassphrase", wrapStringParameter(password), "300"
        );

        // Response is expected to be empty
        if (response.trim().length() > 0) {
            throw new WalletCallException("Unexpected response from wallet: " + response);
        }
    }

    // Wallet locks check - an unencrypted wallet will give an error
    // hush-cli walletlock
    // error: {"code":-15,"message":"Error: running with an unencrypted wallet, but walletlock was called."}
    public synchronized boolean isWalletEncrypted()
            throws WalletCallException, IOException, InterruptedException {
        final String strResult = getCommandLineExecutor(new String[]{ "walletlock" }).execute();

        if (strResult.trim().length() == 0) {
            // If it could be locked with no result - obviously encrypted
            return true;
        } else if (strResult.trim().toLowerCase(Locale.ROOT).startsWith("error:")) {
            // Expecting an error of an unencrypted wallet
            final String jsonPart = strResult.substring(strResult.indexOf("{"));
            try {
                final JsonValue response = Json.parse(jsonPart);
                final JsonObject respObject = response.asObject();
                if ((respObject.getDouble("code", -1) == -15) &&
                            respObject.getString("message", "ERR").contains("unencrypted wallet")) {
                    // Obviously unencrypted
                    return false;
                } else {
                    throw new WalletCallException("Unexpected response from wallet: " + strResult);
                }
            } catch (final ParseException e) {
                throw new WalletCallException(jsonPart + "\n" + e.getMessage() + "\n", e);
            }
        } else if (strResult.trim().toLowerCase(Locale.ROOT).startsWith("error code:")) {
            final JsonObject respObject = jsonifyErrorMessage(strResult);
            if ((respObject.getDouble("code", -1) == -15) &&
                        respObject.getString("message", "ERR").contains("unencrypted wallet")) {
                // Obviously unencrypted
                return false;
            } else {
                throw new WalletCallException("Unexpected response from wallet: " + strResult);
            }
        } else {
            throw new WalletCallException("Unexpected response from wallet: " + strResult);
        }
    }

    /**
     * Encrypts the wallet. Typical success/error use cases are:
     * <p>
     * ./hush-cli encryptwallet "1234"
     * wallet encrypted; Bitcoin server stopping, restart to run with encrypted wallet.
     * The keypool has been flushed, you need to make a new backup.
     * <p>
     * ./hush-cli encryptwallet "1234"
     * error: {"code":-15,"message":"Error: running with an encrypted wallet, but encryptwallet was called."}
     *
     * @param password
     */
    public synchronized void encryptWallet(final String password)
            throws WalletCallException, IOException, InterruptedException {
        final String result = this.executeCommandAndGetSingleStringResponse(
            "encryptwallet", wrapStringParameter(password)
        );
        System.out.println("Result of wallet encryption is: \n" + result);
        // If no exception - obviously successful
    }

    public synchronized void backupWallet(final String fileName)
            throws WalletCallException, IOException, InterruptedException {
        System.out.println("Backup up wallet to location: " + fileName);
        final String result = this.executeCommandAndGetSingleStringResponse(
            "backupwallet", wrapStringParameter(fileName)
        );
        // If no exception - obviously successful
    }

    public synchronized void exportWallet(final String fileName)
            throws WalletCallException, IOException, InterruptedException {
        System.out.println("Export wallet keys to location: " + fileName);
        final String result = this.executeCommandAndGetSingleStringResponse(
            "z_exportwallet", wrapStringParameter(fileName)
        );
        // If no exception - obviously successful
    }

    public synchronized void importWallet(final String fileName)
            throws WalletCallException, IOException, InterruptedException {
        System.out.println("Import wallet keys from location: " + fileName);
        final String result = this.executeCommandAndGetSingleStringResponse(
            "z_importwallet", wrapStringParameter(fileName)
        );
        // If no exception - obviously successful
    }

    public synchronized String getTPrivateKey(final String address)
            throws WalletCallException, IOException, InterruptedException {
        final String result = this.executeCommandAndGetSingleStringResponse(
            "dumpprivkey", wrapStringParameter(address)
        );
        return result.trim();
    }

    public synchronized String getZPrivateKey(final String address)
            throws WalletCallException, IOException, InterruptedException {
        final String result = this.executeCommandAndGetSingleStringResponse(
            "z_exportkey", wrapStringParameter(address)
        );
        return result.trim();
    }

    // Imports a private key - tries both possibilities T/Z
    public synchronized void importPrivateKey(final String key)
            throws WalletCallException, IOException, InterruptedException {
        // First try a Z key
        final String[] params = new String[]{
            "z_importkey",
            wrapStringParameter(key)
        };
        final String result = getCommandLineExecutor(params).execute();

        if ((result == null) || (result.trim().length() == 0)) {
            return;
        }

        // Obviously we have an error trying to import a Z key
        if (result.trim().toLowerCase(Locale.ROOT).startsWith("error:")) {
            // Expecting an error of a T address key
            final String jsonPart = result.substring(result.indexOf("{"));
            try {
                final JsonValue response = Json.parse(jsonPart);
                final JsonObject respObject = response.asObject();
                if ((respObject.getDouble("code", +123) == -1) &&
                            respObject.getString("message", "ERR").contains("wrong network type")) {
                    // Obviously T address - do nothing here
                } else {
                    throw new WalletCallException("Unexpected response from wallet: " + result);
                }
            } catch (final ParseException e) {
                throw new WalletCallException(jsonPart + "\n" + e.getMessage() + "\n", e);
            }
        } else if (result.trim().toLowerCase(Locale.ROOT).startsWith("error code:")) {
            final JsonObject respObject = jsonifyErrorMessage(result);
            if ((respObject.getDouble("code", +123) == -1) &&
                        respObject.getString("message", "ERR").contains("wrong network type")) {
                // Obviously T address - do nothing here
            } else {
                throw new WalletCallException("Unexpected response from wallet: " + result);
            }
        } else {
            throw new WalletCallException("Unexpected response from wallet: " + result);
        }

        // Second try a T key
        final String result2 = this.executeCommandAndGetSingleStringResponse("importprivkey", wrapStringParameter(key));

        if ((result2 == null) || (result2.trim().length() == 0)) {
            return;
        }

        // Obviously an error
        throw new WalletCallException("Unexpected response from wallet: " + result2);
    }

    private JsonObject executeCommandAndGetJsonObject(final String command1, final String command2)
            throws WalletCallException, IOException, InterruptedException {
        final JsonValue response = this.executeCommandAndGetJsonValue(command1, command2);

        if (response.isObject()) {
            return response.asObject();
        } else {
            throw new WalletCallException("Unexpected non-object response from wallet: " + response.toString());
        }
    }

    private JsonArray executeCommandAndGetJsonArray(final String command1, final String command2)
            throws WalletCallException, IOException, InterruptedException {
        return this.executeCommandAndGetJsonArray(command1, command2, null);
    }

    private JsonArray executeCommandAndGetJsonArray(final String command1, final String command2, final String command3)
            throws WalletCallException, IOException, InterruptedException {
        final JsonValue response = this.executeCommandAndGetJsonValue(command1, command2, command3);

        if (response.isArray()) {
            return response.asArray();
        } else {
            throw new WalletCallException("Unexpected non-array response from wallet: " + response.toString());
        }
    }

    private JsonValue executeCommandAndGetJsonValue(final String command1, final String command2)
            throws WalletCallException, IOException, InterruptedException {
        return this.executeCommandAndGetJsonValue(command1, command2, null);
    }

    private JsonValue executeCommandAndGetJsonValue(final String command1, final String command2, final String command3)
            throws WalletCallException, IOException, InterruptedException {
        final String result = this.executeCommandAndGetSingleStringResponse(command1, command2, command3);
        try {
            final JsonValue response = Json.parse(result);
            return response;
        } catch (final ParseException e) {
            throw new WalletCallException(result + "\n" + e.getMessage() + "\n", e);
        }
    }

    private String executeCommandAndGetSingleStringResponse(final String command1)
            throws WalletCallException, IOException, InterruptedException {
        return this.executeCommandAndGetSingleStringResponse(command1, null);
    }

    private String executeCommandAndGetSingleStringResponse(final String command1, final String command2)
            throws WalletCallException, IOException, InterruptedException {
        return this.executeCommandAndGetSingleStringResponse(command1, command2, null);
    }

    private String executeCommandAndGetSingleStringResponse(
        final String command1,
        final String command2,
        final String command3
    ) throws WalletCallException, IOException, InterruptedException {
        final String[] params;
        if (command3 != null) {
            params = new String[]{ command1, command2, command3 };
        } else if (command2 != null) {
            params = new String[]{ command1, command2 };
        } else {
            params = new String[]{ command1 };
        }
        final String result = getCommandLineExecutor(params).execute();

        if (result.trim().toLowerCase(Locale.ROOT).startsWith("error:") ||
                    result.trim().toLowerCase(Locale.ROOT).startsWith("error code:")) {
            throw new WalletCallException("Error response from wallet: " + result);
        }
        return result;
    }

    // Used to wrap string parameters on the command line - not doing so causes problems on Windows.
    private String wrapStringParameter(String param) {
        // Fix is made for Windows only
        // BRX-TODO: This is the only reason for the `isWindowsRuntime` method laziness
        // BRX-TODO: Does it not make sense to also quote these parameters on *nix?
        if (RuntimeEnvironment.isWindowsRuntime()) {
            param = "\"" + param.replace("\"", "\\\"") + "\"";
        }
        return param;
    }

    private void decomposeJSONValue(final String name, final JsonValue val, final Map<String, String> map) {
        if (val.isObject()) {
            final JsonObject obj = val.asObject();
            for (final String memberName : obj.names()) {
                this.decomposeJSONValue(name + "." + memberName, obj.get(memberName), map);
            }
        } else if (val.isArray()) {
            final JsonArray arr = val.asArray();
            for (int i = 0; i < arr.size(); i++) {
                this.decomposeJSONValue(name + "[" + i + "]", arr.get(i), map);
            }
        } else {
            map.put(name, val.toString());
        }
    }

    // BRX-TODO: Is this still needed?
    // Turns a 1.0.7+ error message to a an old json-style message
    // info - new style error message
    private JsonObject jsonifyErrorMessage(final String info) throws IOException {
        final JsonObject result = new JsonObject();

        // Error message here comes from ZCash 1.0.7+ and is like:
        /*
        hush-cli getinfo
        error code: -28
        error message:
        Loading block index...
        */
        final LineNumberReader lnr = new LineNumberReader(new StringReader(info));
        final int errCode = Integer.parseInt(lnr.readLine().substring(11).trim());
        result.set("code", errCode);
        lnr.readLine();
        result.set("message", lnr.readLine().trim());
        return result;
    }

    public static class WalletBalance {
        public double transparentBalance;
        public double privateBalance;
        public double totalBalance;

        public double transparentUnconfirmedBalance;
        public double privateUnconfirmedBalance;
        public double totalUnconfirmedBalance;
    }

    public static class NetworkAndBlockchainInfo {
        public int numConnections;
        public Date lastBlockDate;
    }

    static class WalletCallException extends Exception {
        WalletCallException(final String message) {
            super(message);
        }

        WalletCallException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    class DaemonUnavailableException extends Exception {
        DaemonUnavailableException(final String message) {
            super(message);
        }
    }
}
