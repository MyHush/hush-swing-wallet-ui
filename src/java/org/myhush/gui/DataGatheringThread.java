// Copyright (c) 2016-2017 Ivan Vaklinov <ivan@vaklinov.com>
// Copyright (c) 2018 The Hush Developers <contact@myhush.org>
//
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.myhush.gui;

/**
 * This thread may be used to periodically and asynchronously load data if the load operation
 * takes considerable time. The creator of the thread may obtain the latest gathered data
 * quickly since it is stored in the thread.
 *
 * @param <T> the type of data that is gathered
 */
class DataGatheringThread<T> extends Thread {
    // Last gathered data - stored
    private T lastGatheredData;
    // Gatherer used for the data
    private final DataGatherer<T> gatherer;
    // Interval in ms for gathering
    private final int interval;
    // Fag to run immediately - no wait
    private final boolean doAFirstGathering;
    // Error reporter
    private final StatusUpdateErrorReporter errorReporter;
    // Flag allowing the thread to be suspended
    private boolean suspended;
    /**
     * Creates a new thread for data gathering.
     *
     * @param gatherer      Gatherer used for the data
     * @param errorReporter Error reporter - may be null
     * @param interval      Interval in ms for gathering
     */
    public DataGatheringThread(DataGatherer<T> gatherer, StatusUpdateErrorReporter errorReporter, int interval) {
        this(gatherer, errorReporter, interval, false);
    }

    /**
     * Creates a new thread for data gathering.
     *
     * @param gatherer      Gatherer used for the data
     * @param errorReporter Error reporter - may be null
     * @param interval      Interval in ms for gathering
     */
    DataGatheringThread(DataGatherer<T> gatherer, StatusUpdateErrorReporter errorReporter,
                        int interval, boolean doAFirstGathering
                       ) {
        this.suspended = false;
        this.gatherer = gatherer;
        this.errorReporter = errorReporter;
        this.interval = interval;
        this.doAFirstGathering = doAFirstGathering;

        this.lastGatheredData = null;

        // Start the thread to gather
        this.start();
    }

    /**
     * Sets the suspension flag.
     *
     * @param suspended suspension flag.
     */
    public synchronized void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    /**
     * Obtains the last gathered data
     *
     * @return the last gathered data
     */
    public synchronized T getLastData() {
        return lastGatheredData;
    }

    /**
     * Runs periodically and gathers the data at intervals;
     */
    @Override
    public void run() {
        if (this.doAFirstGathering && (!this.suspended)) {
            this.doOneGathering();
        }

        while (true) {
            synchronized (this) {
                long startWait = System.currentTimeMillis();
                long endWait;
                do {
                    try {
                        this.wait(300);
                    } catch (InterruptedException ie) {
                        // One of the rare cases where we do nothing
                        ie.printStackTrace();
                    }

                    endWait = System.currentTimeMillis();
                } while ((endWait - startWait) <= this.interval);
            }

            if (this.suspended) {
                break;
            }
            this.doOneGathering();
        }
    } // End public void run()

    // Obtains the data in a single run
    private void doOneGathering() {
        // The gathering itself is not synchronized
        T localData = null;

        try {
            localData = this.gatherer.gatherData();
        } catch (Exception e) {
            if (!this.suspended) {
                e.printStackTrace();
                if (this.errorReporter != null) {
                    this.errorReporter.reportError(e);
                }
            } else {
                System.out.println("DataGatheringThread: ignoring " + e.getClass().getName() + " due to suspension!");
            }
        }

        synchronized (this) {
            this.lastGatheredData = localData;
        }
    }


    /**
     * All implementations must provide an impl. of this interface to
     * gather the actual data.
     *
     * @param <T> the type of data that is gathered.
     */
    public interface DataGatherer<T> {
        T gatherData()
                throws Exception;
    }
}
