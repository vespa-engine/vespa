// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.standalone;

import com.yahoo.log.LogLevel;
import com.yahoo.log.LogSetup;
import com.yahoo.log.event.Event;
import com.yahoo.vespa.clustercontroller.core.FleetController;

import java.util.logging.Logger;

/**
 * This is the class containing the main method used to run fleet controller as a stand-alone program.
 */
public class StandAloneClusterController {
    private static Logger log = Logger.getLogger(StandAloneClusterController.class.getName());

    private final ClusterControllerConfigFetcher config;
    private FleetController controller;
    private boolean started = false, stop = false;

    public static class ShutdownHook extends Thread {
        private StandAloneClusterController app;

        public ShutdownHook(StandAloneClusterController app) {
            this.app = app;
        }

        @Override
        public void run() {
            try{
                app.stop();
            } catch (Exception e) {
                log.log(LogLevel.FATAL, "Failed to stop application '" + app.getName() + "': " + e.getMessage());
                e.printStackTrace();
                return;
            }
        }

    }

    public static void main(String args[]) throws Exception {
        runApplication(new StandAloneClusterController(new ClusterControllerConfigFetcher()));
    }

    public static void runApplication(StandAloneClusterController myApp) {
        LogSetup.initVespaLogging("fleetcontroller");
        try{
            myApp.start();
        } catch (Exception e) {
            log.log(LogLevel.FATAL, "Failed to start application '" + myApp.getName() + "': " + e.getMessage());
            e.printStackTrace();
            return;
        }
        Runtime.getRuntime().addShutdownHook(new ShutdownHook(myApp));
        try{
            myApp.run();
        } catch (Exception e) {
            log.log(LogLevel.FATAL, "Application '" + myApp.getName() + "' runtime failure: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public StandAloneClusterController(ClusterControllerConfigFetcher config) {
        this.config = config;
    }

    public String getName() {
        return "Fleetcontroller " + config.getOptions().fleetControllerIndex
             + " of cluster " + config.getOptions().clusterName;
    }

    public void start() throws Exception {
        controller = FleetController.createForStandAlone(config.getOptions());
        Event.started(getName());
    }

    public void run() throws Exception {
        synchronized(this) {
            started = true;
        }
        try{
            while (true) {
                synchronized (this) {
                    if (stop) {
                        notifyAll();
                        return;
                    }
                    if (config.updated(1)) {
                        controller.updateOptions(config.getOptions(), config.getGeneration());
                    }
                    try{ wait(1000); } catch (InterruptedException e) {}
                }
            }
        } finally {
            started = false;
        }
    }

    public void stop() throws Exception {
        Event.stopping(getName(), "controlled shutdown");
        synchronized (this) {
            controller.shutdown();
            stop = true;
            notifyAll();
            while (started) {
                try{ wait(1000); } catch (InterruptedException e) {}
            }
            config.close();
        }
        log.fine("Fleetcontroller done shutting down");
    }

}
