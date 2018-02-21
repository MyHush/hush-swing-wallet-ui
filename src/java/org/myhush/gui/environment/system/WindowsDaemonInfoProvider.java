package org.myhush.gui.environment.system;

import org.myhush.gui.CommandExecutor;
import org.myhush.gui.environment.RuntimeEnvironment;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.StringTokenizer;

public class WindowsDaemonInfoProvider extends DaemonInfoProvider {
    // BRX-TODO: Switch to `tasklist /fo csv /nh` instead of just `tasklist` for CSV output
    // BRX-TODO: ^ https://stackoverflow.com/a/47987808
    @Override
    public DaemonInfo getDaemonInfo() throws IOException, InterruptedException {
        final DaemonInfo info = new DaemonInfo();
        info.status = DaemonState.UNKNOWN;
        info.cpuPercentage = 0;
        info.virtualSizeMB = 0;

        final String hushDaemonFileName = RuntimeEnvironment.getHushDaemonFileName();
        final String tasklist = new CommandExecutor(new String[]{ "tasklist" }).execute();
        // BRX-TODO: Same comment here re. LineNumberReader as elsewhere
        final LineNumberReader lnr = new LineNumberReader(new StringReader(tasklist));

        String line;
        while ((line = lnr.readLine()) != null) {
            final StringTokenizer stringTokenizer = new StringTokenizer(line, " \t", false);
            boolean foundHush = false;
            final StringBuilder size = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (!stringTokenizer.hasMoreTokens()) {
                    break;
                }
                final String token = stringTokenizer.nextToken().replaceAll("^\"|\"$", "");

                if (i == 0) {
                    if (token.equals(hushDaemonFileName)) {
                        info.status = DaemonState.RUNNING;
                        foundHush = true;
                        //System.out.println("Hushd process data is: " + line);
                    }
                } else if ((i >= 4) && foundHush) {
                    try {
                        size.append(token.replaceAll("[^0-9]", ""));
                        if (size.toString().endsWith("K")) {
                            size.setLength(size.length() - 1);
                        }
                    } catch (final NumberFormatException nfe) { /* TODO: Log or handle exception */ }
                }
            } // End parsing row

            if (foundHush) {
                try {
                    info.residentSizeMB = Double.valueOf(size.toString()) / 1000;
                } catch (final NumberFormatException nfe) {
                    info.residentSizeMB = 0;
                    System.out.println("Error: could not find the numeric memory size of hushd: " + size);
                }
                break;
            }
        }

        if (info.status != DaemonState.RUNNING) {
            info.cpuPercentage = 0;
            info.residentSizeMB = 0;
            info.virtualSizeMB = 0;
        }
        return info;
    }
}
