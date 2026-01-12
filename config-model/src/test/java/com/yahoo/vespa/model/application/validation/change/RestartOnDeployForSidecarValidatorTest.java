// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.SidecarSpec;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author glebashnik
 */
public class RestartOnDeployForSidecarValidatorTest {

    private static final String SERVICES_XML = """
        <?xml version='1.0' encoding='utf-8' ?>
        <services version='1.0'>
          <container id='feed' version='1.0'>
            <nodes count='1'/>
            <document-api/>
          </container>
        </services>
        """;

    private static final SidecarSpec SIDECAR_1 = SidecarSpec.builder()
            .id(0)
            .name("triton")
            .image(DockerImage.fromString("nvcr.io/nvidia/tritonserver:25.09-py3"))
            .minCpu(1.0)
            .volumeMounts(List.of("/models"))
            .command(List.of("tritonserver", "--log-verbose=1", "--model-repository=/models"))
            .build();

    private static final SidecarSpec SIDECAR_2 = SidecarSpec.builder()
            .id(0)
            .name("triton")
            .image(DockerImage.fromString("nvcr.io/nvidia/tritonserver:26.01-py3"))
            .minCpu(1.0)
            .volumeMounts(List.of("/models"))
            .command(List.of("tritonserver", "--log-verbose=1", "--model-repository=/models"))
            .build();

    private static final SidecarSpec SIDECAR_3 = SidecarSpec.builder()
            .id(0)
            .name("triton")
            .image(DockerImage.fromString("nvcr.io/nvidia/tritonserver:26.01-py3"))
            .minCpu(2.0)
            .volumeMounts(List.of("/models", "/configs"))
            .command(List.of("tritonserver", "--log-verbose=2"))
            .build();

    @Test
    void no_restart_when_no_sidecars() {
        var current = modelWithSidecars(List.of());
        var next = modelWithSidecars(List.of());
        var result = validate(current, next);
        assertEquals(0, result.size());
    }

    @Test
    void restart_when_sidecar_added() {
        var current = modelWithSidecars(List.of());
        var next = modelWithSidecars(List.of(SIDECAR_1));
        var result = validate(current, next);

        assertEquals(1, result.size());
        assertRestartActionProperties(
                result.get(0),
                "Need to restart services in cluster 'feed' due to added sidecars: 'triton'");
    }

    @Test
    void restart_when_sidecar_changed() {
        var current = modelWithSidecars(List.of(SIDECAR_1));
        var next = modelWithSidecars(List.of(SIDECAR_3));
        var result = validate(current, next);

        assertEquals(1, result.size());
        assertRestartActionProperties(
                result.get(0),
                "Need to restart services in cluster 'feed' due to changed sidecars: 'triton' ("
                        + "image: 'nvcr.io/nvidia/tritonserver:25.09-py3' -> 'nvcr.io/nvidia/tritonserver:26.01-py3', "
                        + "minCpu: 1.0 -> 2.0, "
                        + "volumeMounts: [/models] -> [/models, /configs], "
                        + "command: [tritonserver, --log-verbose=1, --model-repository=/models] -> [tritonserver, --log-verbose=2])");
    }

    @Test
    void no_restart_when_sidecars_unchanged() {
        var current = modelWithSidecars(List.of(SIDECAR_1));
        var next = modelWithSidecars(List.of(SIDECAR_1));
        var result = validate(current, next);

        assertEquals(0, result.size());
    }

    @Test
    void restart_when_sidecar_removed() {
        var current = modelWithSidecars(List.of(SIDECAR_1));
        var next = modelWithSidecars(List.of());
        var result = validate(current, next);

        assertEquals(1, result.size());
        assertRestartActionProperties(
                result.get(0),
                "Need to restart services in cluster 'feed' due to removed sidecars: 'triton'");
    }

    private List<ConfigChangeAction> validate(VespaModel current, VespaModel next) {
        return ValidationTester.validateChanges(
                new RestartOnDeployForSidecarValidator(),
                next,
                new DeployState.Builder().previousModel(current).build());
    }

    private static void assertRestartActionProperties(ConfigChangeAction action, String expectedMessage) {
        assertEquals(expectedMessage, action.getMessage());
        assertFalse(action.ignoreForInternalRedeploy());
        assertEquals(ConfigChangeAction.Type.RESTART, action.getType());
        var restartAction = assertInstanceOf(VespaRestartAction.class, action);
        assertEquals(VespaRestartAction.ConfigChange.DEFER_UNTIL_RESTART, restartAction.configChange());
    }

    private VespaModel modelWithSidecars(List<SidecarSpec> sidecars) {
        var properties = new TestProperties();
        properties.setHostedVespa(true);
        var deployState = new DeployState.Builder()
                .properties(properties)
                .endpoints(Set.of(new ContainerEndpoint(
                        "feed", ApplicationClusterEndpoint.Scope.zone, List.of("c1.example.com"))))
                .modelHostProvisioner(new ProvisionerWithSidecars(sidecars));

        return new VespaModelCreatorWithMockPkg(null, SERVICES_XML).create(deployState);
    }

    private static class ProvisionerWithSidecars implements HostProvisioner {
        private final List<SidecarSpec> sidecars;
        private int hostsCreated = 0;

        ProvisionerWithSidecars(List<SidecarSpec> sidecars) {
            this.sidecars = sidecars;
        }

        @Override
        public HostSpec allocateHost(String alias) {
            return new HostSpec(alias, Optional.empty());
        }

        @Override
        public List<HostSpec> prepare(ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
            var hosts = new ArrayList<HostSpec>();
            var resources = capacity.minResources().nodeResources();

            // Rebuild cluster spec with sidecars
            var clusterWithSidecars = ClusterSpec.specification(cluster.type(), cluster.id())
                    .group(cluster.group().orElse(null))
                    .vespaVersion(cluster.vespaVersion())
                    .exclusive(cluster.isExclusive())
                    .dockerImageRepository(cluster.dockerImageRepo())
                    .loadBalancerSettings(cluster.zoneEndpoint())
                    .stateful(cluster.isStateful())
                    .sidecars(sidecars)
                    .build();

            for (int i = 0; i < capacity.minResources().nodes(); i++) {
                hosts.add(new HostSpec(
                        "host" + (hostsCreated++),
                        resources,
                        resources,
                        resources,
                        ClusterMembership.from(clusterWithSidecars, i),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()));
            }
            return hosts;
        }
    }
}
