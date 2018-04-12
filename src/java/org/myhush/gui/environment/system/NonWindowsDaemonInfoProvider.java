package org.myhush.gui.environment.system;

import org.myhush.gui.CommandExecutor;
import org.myhush.gui.environment.RuntimeEnvironment;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.StringTokenizer;

public class NonWindowsDaemonInfoProvider extends DaemonInfoProvider {
    // BRX-TODO: OS-specific logic needs to be abstracted
    private class NixProcessStatus {
        double cpuPercentage;
        double virtualSizeMB;
        double residentSizeMB;
        String command;
    }

    private NixProcessStatus getProcessStatus(final String processStatusRow) {
        final StringTokenizer stringTokenizer = new StringTokenizer(processStatusRow, " \t", false);
        final NixProcessStatus processStatus = new NixProcessStatus();

        for (int col = 0; col < 11; col++) {
            final String token;
            if (stringTokenizer.hasMoreTokens()) {
                token = stringTokenizer.nextToken();
            } else {
                break;
            }
            switch (col) {
                case 2:
                    try {
                        processStatus.cpuPercentage = Double.valueOf(token);
                    } catch (final NumberFormatException e) { /* TODO: Log or handle exception */ }
                    break;
                case 4:
                    try {
                        processStatus.virtualSizeMB = Double.valueOf(token) / 1000;
                    } catch (final NumberFormatException e) { /* TODO: Log or handle exception */ }
                    break;
                case 5:
                    try {
                        processStatus.residentSizeMB = Double.valueOf(token) / 1000;
                    } catch (final NumberFormatException e) { /* TODO: Log or handle exception */ }
                    break;
                case 10:
                    processStatus.command = token;
                    break;
            }
        }
        return processStatus;
    }

    // So far tested on Mac OS X and Linux - expected to work on other UNIXes as well
    @Override
    public DaemonInfo getDaemonInfo() throws IOException, InterruptedException {
        final DaemonInfo info = new DaemonInfo();
        info.status = DaemonState.UNKNOWN;

        final String hushDaemonFileName = RuntimeEnvironment.getHushDaemonFileName();
        final String psAuxResult = new CommandExecutor(new String[]{ "ps", "auxwww" }).execute();
        final LineNumberReader lineReader = new LineNumberReader(new StringReader(psAuxResult));

        do {
            final String line = lineReader.readLine();
            if (line == null) {
                break;
            }
            final NixProcessStatus processStatus = getProcessStatus(line);

            if (processStatus.command.equals(hushDaemonFileName) || processStatus.command.endsWith("/" + hushDaemonFileName)) {
                info.cpuPercentage = processStatus.cpuPercentage;
                info.residentSizeMB = processStatus.residentSizeMB;
                info.virtualSizeMB = processStatus.virtualSizeMB;
                info.status = DaemonState.RUNNING;
                break;
            }
        } while (true);

        if (info.status != DaemonState.RUNNING) {
            info.cpuPercentage = 0;
            info.residentSizeMB = 0;
            info.virtualSizeMB = 0;
        }
        return info;
    }
}
