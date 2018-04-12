// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Executes a command and returns the result.
 */
public class CommandExecutor {
    private final String[] args;

    public CommandExecutor(final String args[]) {
        this.args = args;
    }

    public Process startChildProcess() throws IOException {
        return Runtime.getRuntime().exec(args);
    }

    // BRX-TODO: Is this inefficient? Looks like it may need a lot of CPU usage
    // BRX-TODO: Also, why are we returning one string for two types of results?
    public String execute() throws IOException, InterruptedException {
        final StringBuffer result = new StringBuffer();
        final Runtime runtime = Runtime.getRuntime();
        final Process process = runtime.exec(args);
        final BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
        final BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        final Thread inThread = new Thread(
            () -> {
                try {
                    do {
                        final int c = in.read();
                        if (c == -1) {
                            break;
                        }
                        result.append((char) c);
                    } while (true);
                } catch (final IOException ioe) {
                    // TODO: log or handle the exception
                }
            }
        );
        inThread.start();

        final Thread errThread = new Thread(
            () -> {
                try {
                    do {
                        final int c = err.read();
                        if (c == -1) {
                            break;
                        }
                        result.append((char) c);
                    } while (true);
                } catch (final IOException ioe) {
                    // TODO: log or handle the exception
                }
            }
        );
        errThread.start();

        process.waitFor();
        inThread.join();
        errThread.join();

        return result.toString();
    }
}
