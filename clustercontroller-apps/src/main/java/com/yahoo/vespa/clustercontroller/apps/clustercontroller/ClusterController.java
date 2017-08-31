// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.clustercontroller.apputil.communication.http.JDiscMetricWrapper;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTaskScheduler;
import com.yahoo.vespa.clustercontroller.core.restapiv2.ClusterControllerStateRestAPI;
import com.yahoo.vespa.clustercontroller.core.status.StatusHandler;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.zookeeper.ZooKeeperServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Wrapper around fleet controller state to be able to use it in container.
 */
public class ClusterController extends AbstractComponent
                               implements ClusterControllerStateRestAPI.FleetControllerResolver,
                                          StatusHandler.ClusterStatusPageServerSet {

    private static final Logger log = Logger.getLogger(ClusterController.class.getName());
    private final JDiscMetricWrapper metricWrapper;
    private final Map<String, FleetController> controllers = new TreeMap<>();
    private final Map<String, StatusHandler.ContainerStatusPageServer> status = new TreeMap<>();

    /**
     * Dependency injection constructor for controller. {@link ZooKeeperProvider} argument given
     * to ensure that zookeeper has started before we start polling it.
     */
    @Inject
    public ClusterController(ZooKeeperProvider zooKeeperProvider) {
        this();
    }

    ClusterController() {
        metricWrapper = new JDiscMetricWrapper(null);
    }


    public void setOptions(String clusterName, FleetControllerOptions options, Metric metricImpl) throws Exception {
        metricWrapper.updateMetricImplementation(metricImpl);
        if (options.zooKeeperServerAddress != null && !"".equals(options.zooKeeperServerAddress)) {
            // Wipe this path ... it's unclear why
            String path = "/" + options.clusterName + options.fleetControllerIndex;
            Curator curator = Curator.create(options.zooKeeperServerAddress);
            if (curator.framework().checkExists().forPath(path) != null)
                curator.framework().delete().deletingChildrenIfNeeded().forPath(path);
            curator.framework().create().creatingParentsIfNeeded().forPath(path);
        }
        synchronized (controllers) {
            FleetController controller = controllers.get(clusterName);

            if (controller == null) {
                StatusHandler.ContainerStatusPageServer statusPageServer = new StatusHandler.ContainerStatusPageServer();
                controller = FleetController.createForContainer(options, statusPageServer, metricWrapper);
                controllers.put(clusterName, controller);
                status.put(clusterName, statusPageServer);
            } else {
                controller.updateOptions(options, 0);
            }
        }
    }

    @Override
    public void deconstruct() {
        synchronized (controllers) {
            for (FleetController controller : controllers.values()) {
                try{
                    shutdownController(controller);
                } catch (Exception e) {
                    log.warning("Failed to shut down fleet controller: " + e.getMessage());
                }
            }
        }
        super.deconstruct();
    }

    @Override
    public Map<String, RemoteClusterControllerTaskScheduler> getFleetControllers() {
        Map<String, RemoteClusterControllerTaskScheduler> m = new LinkedHashMap<>();
        synchronized (controllers) {
            m.putAll(controllers);
        }
        return m;
    }

    @Override
    public StatusHandler.ContainerStatusPageServer get(String cluster) {
        return status.get(cluster);
    }

    @Override
    public Map<String, StatusHandler.ContainerStatusPageServer> getAll() {
        return status;
    }

    void shutdownController(FleetController controller) throws Exception {
        controller.shutdown();
    }

}
