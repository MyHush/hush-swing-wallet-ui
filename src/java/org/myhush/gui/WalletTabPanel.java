// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Base for all panels contained as wallet TABS.
 */
class WalletTabPanel extends JPanel {
    // Lists of threads and timers that may be stopped if necessary
    List<Timer> timers;
    List<DataGatheringThread<?>> threads;


    WalletTabPanel() {
        super();

        this.timers = new ArrayList<>();
        this.threads = new ArrayList<>();
    }

    public void stopThreadsAndTimers() {
        for (Timer timer : timers) {
            timer.stop();
        }
        for (DataGatheringThread<?> thread : threads) {
            thread.setSuspended(true);
        }
    }

}
