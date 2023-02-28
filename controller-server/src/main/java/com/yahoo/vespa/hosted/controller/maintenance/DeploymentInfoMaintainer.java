package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.Collection;

/**
 * This pulls application deployment information from the node repo on all config servers,
 * and stores it in memory in controller.applications().deploymentInfo().
 *
 * @author bratseth
 */
public class DeploymentInfoMaintainer extends ControllerMaintainer {

    private final NodeRepository nodeRepository;

    public DeploymentInfoMaintainer(Controller controller, Duration duration) {
        super(controller, duration);
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
    }

    @Override
    protected double maintain() {
        int attempts = 0;
        int failures = 0;
        for (var application : controller().applications().asList()) {
            for (var instance : application.instances().values()) {
                for (var deployment : instanceDeployments(instance)) {
                    attempts++;
                    if ( ! updateDeploymentInfo(deployment))
                        failures++;
                }
            }
        }
        return asSuccessFactor(attempts, failures);
    }

    private Collection<DeploymentId> instanceDeployments(Instance instance) {
        return instance.deployments().keySet().stream()
                       .filter(zoneId -> !zoneId.environment().isTest())
                       .map(zoneId -> new DeploymentId(instance.id(), zoneId))
                       .toList();
    }

    private boolean updateDeploymentInfo(DeploymentId id) {
        try {
            controller().applications().deploymentInfo().put(id, nodeRepository.getApplication(id.zoneId(), id.applicationId()));
            return true;
        }
        catch (ConfigServerException e) {
            log.info("Could not retrieve deployment info for " + id + ": " + Exceptions.toMessageString(e));
            return false;
        }
    }

}
