package org.myhush.gui.environment.system;

import java.io.IOException;

public abstract class DaemonInfoProvider {
    public abstract DaemonInfo getDaemonInfo() throws IOException, InterruptedException;
}
