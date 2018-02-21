// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Fetches the proving key
 *
 * _This is deliberately hardcoded_
 */
class ProvingKeyFetcher {

    private static final int PROVING_KEY_SIZE = 910173851;
    private static final String SHA256 = "8bc20a7f013b2b58970cddd2e7ea028975c88ae7ceb9259a5344a16bc2c0eef7";
    private static final String pathURL = "https://zcash.dl.mercerweiss.com/sprout-proving.key";
    // TODO: add backups

    private static void copy(final InputStream is, final OutputStream os) throws IOException {
        final byte[] buf = new byte[0x1 << 13];
        int read;
        while ((read = is.read(buf)) > -0) {
            os.write(buf, 0, read);
        }
        os.flush();
    }

    private static boolean checkSHA256(final File provingKey, final Component parent) throws IOException {
        final MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException impossible) {
            throw new RuntimeException(impossible);
        }
        try (final InputStream is = new BufferedInputStream(new FileInputStream(provingKey))) {
            final ProgressMonitorInputStream pmis = new ProgressMonitorInputStream(parent, "Verifying proving key", is);
            pmis.getProgressMonitor().setMaximum(PROVING_KEY_SIZE);
            pmis.getProgressMonitor().setMillisToPopup(10);
            final DigestInputStream dis = new DigestInputStream(pmis, sha256);
            final byte[] temp = new byte[0x1 << 13];
            while (dis.read(temp) >= 0) {
                /* do the thing */
            }
            final byte[] digest = sha256.digest();
            return SHA256.equalsIgnoreCase(DatatypeConverter.printHexBinary(digest));
        }
    }

    public void fetchIfMissing(final StartupProgressDialog parent) throws IOException {
        try {
            verifyOrFetch(parent);
        } catch (final InterruptedIOException iox) {
            JOptionPane.showMessageDialog(parent, "HUSH cannot proceed without a proving key.");
            System.exit(-3);
        }
    }

    // BRX-NOTE: We're tied to 'ZcashParams' right now due to the `hushd` treatment of location this:
    // @see https://github.com/MyHush/hush/blob/12677875f21c165caf481284ddd45356411c149c/src/util.cpp#L479
    private void verifyOrFetch(final StartupProgressDialog parent) throws IOException {
        final File zcashParams = App.PATH_PROVIDER.getZcashParamsDirectory();
        if (!zcashParams.exists()) {
            zcashParams.mkdirs();
        }

        // verifying key is small, always copy it
        {
            final File verifyingKeyFile = new File(zcashParams, "sprout-verifying.key");
            final FileOutputStream fos = new FileOutputStream(verifyingKeyFile);
            final InputStream is = ProvingKeyFetcher.class.getClassLoader().getResourceAsStream("keys/sprout-verifying.key");
            copy(is, fos);
            fos.close();
        }

        final File provingKeyFile = new File(zcashParams, "sprout-proving.key");
        if (provingKeyFile.exists() && provingKeyFile.length() == PROVING_KEY_SIZE) {
            /*
            * We skip proving key verification every start - this is impractical.
            * If the proving key exists and is the correct size, then it should be OK.
            *
            *   parent.setProgressText("Verifying proving key...");
            *   needsFetch = !checkSHA256(provingKeyFile, parent);
            */
            return;
        }

        JOptionPane.showMessageDialog(
                parent,
                "The wallet needs to download the Z cryptographic proving key (approx. 900 MB).\n" +
                "This will be done only once. Please be patient... Press OK to continue"
        );
        parent.setProgressText("Downloading proving key...");
        provingKeyFile.delete();

        final OutputStream os = new BufferedOutputStream(new FileOutputStream(provingKeyFile));
        final URL keyURL = new URL(pathURL);
        InputStream is = null;
        try {
            is = keyURL.openStream();
            final ProgressMonitorInputStream pmis = new ProgressMonitorInputStream(parent, "Downloading proving key", is);
            pmis.getProgressMonitor().setMaximum(PROVING_KEY_SIZE);
            pmis.getProgressMonitor().setMillisToPopup(10);

            copy(pmis, os);
            os.close();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (final IOException ignore) {}
        }
        parent.setProgressText("Verifying downloaded proving key...");
        if (!checkSHA256(provingKeyFile, parent)) {
            JOptionPane.showMessageDialog(parent, "Failed to download proving key properly. Cannot continue!");
            System.exit(-4);
        }
    }
}
