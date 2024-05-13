// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.clustercontroller.apputil.communication.http.JDiscMetricWrapper;
import com.yahoo.vespa.clustercontroller.core.FleetController;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.clustercontroller.core.RemoteClusterControllerTaskScheduler;
import com.yahoo.vespa.clustercontroller.core.restapiv2.ClusterControllerStateRestAPI;
import com.yahoo.vespa.clustercontroller.core.status.StatusHandler;
import com.yahoo.vespa.zookeeper.server.VespaZooKeeperServer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Wrapper around fleet controller state to be able to use it in container.
 */
public class ClusterController extends AbstractComponent
                               implements ClusterControllerStateRestAPI.FleetControllerResolver,
                                          StatusHandler.ClusterStatusPageServerSet {

    private static final Logger log = Logger.getLogger(ClusterController.class.getName());
    private final JDiscMetricWrapper metricWrapper;
    private final Object monitor = new Object();
    private final Map<String, FleetController> controllers = new TreeMap<>();
    private final Map<String, StatusHandler.ContainerStatusPageServer> status = new TreeMap<>();
    private final Map<String, Integer> referents = new HashMap<>();
    private final AtomicBoolean shutdown = new AtomicBoolean();

    /**
     * Dependency injection constructor for controller. A {@link VespaZooKeeperServer} argument is required
     * for all its users, to ensure that zookeeper has started before we start polling it, but
     * should not be injected here, as that causes recreation of the cluster controller, and old and new
     * will run master election, etc., concurrently, which breaks everything.
     */
    @Inject
    public ClusterController() {
        metricWrapper = new JDiscMetricWrapper(null);
    }

    public void setOptions(FleetControllerOptions options, Metric metricImpl) throws Exception {
        metricWrapper.updateMetricImplementation(metricImpl);
        synchronized (monitor) {
            referents.merge(options.clusterName(), 1, Integer::sum);
            FleetController controller = controllers.get(options.clusterName());
            if (controller == null) {
                controller = FleetController.create(options, metricWrapper);
                controllers.put(options.clusterName(), controller);
                status.put(options.clusterName(), controller.statusPageServer());
            } else {
                controller.updateOptions(options);
            }
        }
    }

    @Override
    public void deconstruct() {
        shutdown();
    }

    /**
     * Since we hack around injecting a running ZK here by providing one through the configurer instead,
     * we must also let the last configurer shut down this controller, to ensure this is shut down
     * before the ZK server it had injected from the configurers.
     */
    void countdown(String clusterName) {
        synchronized (monitor) {
            referents.compute(clusterName, (__, count) -> {
                if (count == null) throw new IllegalStateException("trying to remove unknown cluster: " + clusterName);
                if (count == 1) {
                    shutDownController(controllers.remove(clusterName));
                    status.remove(clusterName);
                    return null;
                }
                return count - 1;
            });
        }
    }

    private void shutDownController(FleetController controller) {
        if (controller == null) return;
        try {
            controller.shutdown();
        } catch (Exception e) {
            log.warning("Failed to shut down fleet controller: " + e.getMessage());
        }
    }

    void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            synchronized (monitor) {
                for (FleetController controller : controllers.values()) {
                    shutDownController(controller);
                }
            }
        }
    }

    @Override
    public Map<String, RemoteClusterControllerTaskScheduler> getFleetControllers() {
        synchronized (monitor) {
            return new LinkedHashMap<>(controllers);
        }
    }

    @Override
    public StatusHandler.ContainerStatusPageServer get(String cluster) {
        return status.get(cluster);
    }

    @Override
    public Map<String, StatusHandler.ContainerStatusPageServer> getAll() {
        return status;
    }

}
