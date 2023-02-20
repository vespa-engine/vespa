package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServerException;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.stream.Stream;

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
        controller().applications().asList().stream()
                                   .flatMap(this::mapApplicationToInstances)
                                   .flatMap(this::mapInstanceToDeployments)
                                   .forEach(this::updateDeploymentInfo);
        return 1.0;
    }

    private Stream<Instance> mapApplicationToInstances(Application application) {
        return application.instances().values().stream();
    }

    private Stream<DeploymentId> mapInstanceToDeployments(Instance instance) {
        return instance.deployments().keySet().stream()
                       .filter(zoneId -> !zoneId.environment().isTest())
                       .map(zoneId -> new DeploymentId(instance.id(), zoneId));
    }

    private void updateDeploymentInfo(DeploymentId id) {
        try {
            controller().applications().deploymentInfo().put(id, nodeRepository.getApplication(id.zoneId(), id.applicationId()));
        }
        catch (ConfigServerException e) {
            log.info("Could not retrieve deployment info for " + id + ": " + Exceptions.toMessageString(e));
        }
    }

}
