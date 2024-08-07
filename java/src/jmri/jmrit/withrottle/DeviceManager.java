package jmri.jmrit.withrottle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface for WiThrottle device managers.
 *
 * @author Randall Wood Copyright 2011, 2017
 */
public interface DeviceManager {

    void listen();

    default void createServerThread() {
        new DeviceManagerThread(this).start();
    }

    /**
     * Add a device listener that will be added for each new
     * device connection
     *
     * @param dl the device listener to add 
     */
    void addDeviceListener(DeviceListener dl);

    /**
     * Remove a device listener from the list that will be added for each new
     * device connection
     *
     * @param dl the device listener to remove
     */
    void removeDeviceListener(DeviceListener dl);

    /**
     * Specify a roster group to send
     *
     * @param group the roster group. 
     */
    void setSelectedRosterGroup(String group);

    /**
     * the roster group to send. 
     *
     * @return the roster group
     */
    String getSelectedRosterGroup();

    /**
     * Container for running {@link #listen() } in a separate thread.
     */
    class DeviceManagerThread extends Thread {

        DeviceManager manager;

        DeviceManagerThread(DeviceManager manager) {
            this.manager = manager;
            this.setName("WiThrottleServer"); // NOI18N
        }

        @Override
        public void run() {
            manager.listen();
            log.debug("Leaving DeviceManagerThread.run()");
        }

        private final static Logger log = LoggerFactory.getLogger(DeviceManagerThread.class);
    }
}
