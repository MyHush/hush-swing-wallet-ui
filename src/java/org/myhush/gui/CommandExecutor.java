// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * Executes a command and returns the result.
 */
class CommandExecutor {
    private final String[] args;

    CommandExecutor(String args[]) {
        this.args = args;
    }


    public Process startChildProcess()
            throws IOException {
        return Runtime.getRuntime().exec(args);
    }


    public String execute()
            throws IOException, InterruptedException {
        final StringBuffer result = new StringBuffer();

        Runtime rt = Runtime.getRuntime();
        Process proc = rt.exec(args);

        final Reader in = new InputStreamReader(proc.getInputStream());

        final Reader err = new InputStreamReader(proc.getErrorStream());

        Thread inThread = new Thread(
                () -> {
                    try {
                        int c;
                        while ((c = in.read()) != -1) {
                            result.append((char) c);
                        }
                    } catch (IOException ioe) {
                        // TODO: log or handle the exception
                    }
                }
        );
        inThread.start();

        Thread errThread = new Thread(
                () -> {
                    try {
                        int c;
                        while ((c = err.read()) != -1) {
                            result.append((char) c);
                        }
                    } catch (IOException ioe) {
                        // TODO: log or handle the exception
                    }
                }
        );
        errThread.start();

        proc.waitFor();
        inThread.join();
        errThread.join();

        return result.toString();
    }
}
