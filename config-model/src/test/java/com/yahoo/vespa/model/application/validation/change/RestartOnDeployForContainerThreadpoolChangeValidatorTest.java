// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.ValidationTester;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * @author glebashnik
 */
public class RestartOnDeployForContainerThreadpoolChangeValidatorTest {

    @Test
    void no_restart_when_not_hosted() {
        var current = model(searchCluster("<threadpool><threads max='8'>4</threads></threadpool>"));
        var next = model(searchCluster("<threadpool><threads max='16'>8</threads></threadpool>"));
        var result = validate(current, next, false);
        assertEquals(0, result.size());
    }

    @Test
    void no_restart_when_threadpool_unchanged() {
        var current = model(searchCluster("<threadpool><threads max='8'>4</threads><queue>100</queue></threadpool>"));
        var next = model(searchCluster("<threadpool><threads max='8'>4</threads><queue>100</queue></threadpool>"));
        var result = validate(current, next, true);
        assertEquals(0, result.size());
    }

    @Test
    void restart_when_search_threadpool_changed() {
        var current = model(searchCluster("<threadpool><threads max='8'>4</threads></threadpool>"));
        var next = model(searchCluster("<threadpool><threads max='16'>8</threads></threadpool>"));
        var result = validate(current, next, true);

        assertEquals(1, result.size());
        assertRestartActionProperties(
                result.get(0),
                """
                Container threadpool 'search-handler' has changed, need to restart services in cluster 'search':
                # Minimum number of threads relative to number of cores (vcpu)
                container-threadpool.relativeMinThreads has changed from 4.0 to 8.0
                # Maximum number of threads relative to number of cores (vcpu)
                container-threadpool.relativeMaxThreads has changed from 8.0 to 16.0""");
    }

    @Test
    void restart_when_threadpool_element_added() {
        // The threadpool component exists in both models (created by the search handler),
        // adding the element changes its config from the defaults.
        var current = model(searchCluster(""));
        var next = model(searchCluster("<threadpool><threads max='8'>4</threads></threadpool>"));
        var result = validate(current, next, true);

        assertEquals(1, result.size());
        assertRestartActionProperties(
                result.get(0),
                """
                Container threadpool 'search-handler' has changed, need to restart services in cluster 'search':
                # Minimum number of threads relative to number of cores (vcpu)
                container-threadpool.relativeMinThreads has changed from 10.0 to 4.0
                # Maximum number of threads relative to number of cores (vcpu)
                container-threadpool.relativeMaxThreads has changed from 10.0 to 8.0""");
    }

    @Test
    void restart_when_threadpool_element_removed() {
        var current = model(searchCluster("<threadpool><threads max='8'>4</threads></threadpool>"));
        var next = model(searchCluster(""));
        var result = validate(current, next, true);

        assertEquals(1, result.size());
        assertRestartActionProperties(
                result.get(0),
                """
                Container threadpool 'search-handler' has changed, need to restart services in cluster 'search':
                # Minimum number of threads relative to number of cores (vcpu)
                container-threadpool.relativeMinThreads has changed from 4.0 to 10.0
                # Maximum number of threads relative to number of cores (vcpu)
                container-threadpool.relativeMaxThreads has changed from 8.0 to 10.0""");
    }

    @Test
    void restart_when_deprecated_absolute_threadpool_changed() {
        var current = model(searchCluster("<threadpool><min-threads>4</min-threads><max-threads>8</max-threads>" +
                                          "<queue-size>100</queue-size></threadpool>"));
        var next = model(searchCluster("<threadpool><min-threads>8</min-threads><max-threads>16</max-threads>" +
                                       "<queue-size>200</queue-size></threadpool>"));
        var result = validate(current, next, true);

        assertEquals(1, result.size());
        assertRestartActionProperties(
                result.get(0),
                """
                Container threadpool 'search-handler' has changed, need to restart services in cluster 'search':
                # Maximum number of thread in the thread pool (absolute)
                container-threadpool.maxThreads has changed from 8 to 16
                # Minimum number of thread in the thread pool (absolute)
                container-threadpool.minThreads has changed from 4 to 8
                # Absolute queue capacity
                container-threadpool.queueSize has changed from 100 to 200""");
    }

    @Test
    void restart_when_user_config_override_changed() {
        // A user <config> override on the cluster applies to all threadpools in it.
        var current = model(searchClusterWithConfigOverride(200));
        var next = model(searchClusterWithConfigOverride(400));
        var result = validate(current, next, true);

        assertFalse(result.isEmpty());
        var messages = result.stream().map(ConfigChangeAction::getMessage).toList();
        assertEquals(result.size(), messages.stream()
                .filter(message -> message.contains("container-threadpool.maxThreads has changed from 200 to 400"))
                .count());
        assertEquals(1, messages.stream()
                .filter(message -> message.startsWith("Container threadpool 'search-handler' has changed"))
                .count());
    }

    @Test
    void restart_when_docproc_threadpool_changed() {
        var current = model(docprocCluster("<threadpool><threads>4</threads></threadpool>"));
        var next = model(docprocCluster("<threadpool><threads>8</threads></threadpool>"));
        var result = validate(current, next, true);

        assertEquals(1, result.size());
        assertRestartActionProperties(
                result.get(0),
                """
                Container threadpool 'docproc-handler' has changed, need to restart services in cluster 'search':
                # Minimum number of threads relative to number of cores (vcpu)
                container-threadpool.relativeMinThreads has changed from 4.0 to 8.0
                # Maximum number of threads relative to number of cores (vcpu)
                container-threadpool.relativeMaxThreads has changed from 4.0 to 8.0""");
    }

    @Test
    void no_restart_for_new_cluster() {
        var current = model(extraCluster("<threadpool><threads max='8'>4</threads></threadpool>"));
        var next = model(searchCluster("<threadpool><threads max='16'>8</threads></threadpool>"));
        var result = validate(current, next, true);
        assertEquals(0, result.size());
    }

    private List<ConfigChangeAction> validate(VespaModel current, VespaModel next, boolean hosted) {
        return ValidationTester.validateChanges(
                new RestartOnDeployForContainerThreadpoolChangeValidator(),
                next,
                new DeployState.Builder()
                        .properties(new TestProperties().setHostedVespa(hosted))
                        .previousModel(current)
                        .build());
    }

    private static void assertRestartActionProperties(ConfigChangeAction action, String expectedMessage) {
        assertEquals(expectedMessage, action.getMessage());
        assertFalse(action.ignoreForInternalRedeploy());
        assertEquals(ConfigChangeAction.Type.RESTART, action.getType());
        var restartAction = assertInstanceOf(VespaRestartAction.class, action);
        assertEquals(VespaRestartAction.ConfigChange.DEFER_UNTIL_RESTART, restartAction.configChange());
    }

    private static String searchCluster(String threadpoolXml) {
        return """
            <container id='search' version='1.0'>
              <nodes count='1'/>
              <search>
                %s
              </search>
            </container>
            """.formatted(threadpoolXml);
    }

    private static String searchClusterWithConfigOverride(int maxThreads) {
        return """
            <container id='search' version='1.0'>
              <nodes count='1'/>
              <config name='container.handler.threadpool.container-threadpool'>
                <maxThreads>%d</maxThreads>
              </config>
              <search/>
            </container>
            """.formatted(maxThreads);
    }

    private static String docprocCluster(String threadpoolXml) {
        return """
            <container id='search' version='1.0'>
              <nodes count='1'/>
              <document-processing>
                %s
              </document-processing>
            </container>
            """.formatted(threadpoolXml);
    }

    private static String extraCluster(String threadpoolXml) {
        return """
            <container id='extra' version='1.0'>
              <nodes count='1'/>
              <search>
                %s
              </search>
            </container>
            """.formatted(threadpoolXml);
    }

    private VespaModel model(String clustersXml) {
        var servicesXml = """
            <?xml version='1.0' encoding='utf-8' ?>
            <services version='1.0'>
              %s
            </services>
            """.formatted(clustersXml);
        var properties = new TestProperties();
        properties.setHostedVespa(true);
        var deployState = new DeployState.Builder()
                .properties(properties)
                .endpoints(Set.of(endpoint("search"), endpoint("extra")));
        return new VespaModelCreatorWithMockPkg(null, servicesXml).create(deployState);
    }

    private static ContainerEndpoint endpoint(String clusterId) {
        return new ContainerEndpoint(clusterId, ApplicationClusterEndpoint.Scope.zone,
                                     List.of(clusterId + ".example.com"));
    }

}
