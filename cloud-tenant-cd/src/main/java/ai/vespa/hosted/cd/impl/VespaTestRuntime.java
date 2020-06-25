// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.impl;

import ai.vespa.cloud.Zone;
import ai.vespa.hosted.api.ControllerHttpClient;
import ai.vespa.hosted.api.Properties;
import ai.vespa.hosted.api.TestConfig;
import ai.vespa.hosted.cd.Deployment;
import ai.vespa.hosted.cd.TestRuntime;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author mortent
 */
public class VespaTestRuntime implements TestRuntime {
    private final TestConfig config;
    private final Deployment deploymentToTest;

    /*
     * Used when executing tests locally
     */
    public VespaTestRuntime() {
            this(configFromPropertyOrController());
    }

    /*
     * Used when executing tests from using Vespa test framework in container
     */
    public VespaTestRuntime(byte[] config) {
        this(fromByteArray(config));
    }
    private VespaTestRuntime(TestConfig config) {
        this.config = config;
        this.deploymentToTest = new CloudDeployment(config.deployments().get(config.zone()), new ai.vespa.hosted.auth.EndpointAuthenticator(config.system()));
    }

    @Override
    public Zone zone() {
        return new Zone(
                ai.vespa.cloud.Environment.valueOf(config.zone().environment().name()),
                config.zone().region().value()); }

    /** Returns the deployment this is testing. */
    @Override
    public Deployment deploymentToTest() { return deploymentToTest; }

    private static TestConfig configFromPropertyOrController() {
        String configPath = System.getProperty("vespa.test.config");
        return configPath != null ? fromFile(configPath) : fromController();
    }

    private static TestConfig fromFile(String path) {
        try {
            return TestConfig.fromJson(Files.readAllBytes(Paths.get(path)));
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed reading config from '" + path + "'!", e);
        }
    }

    private static TestConfig fromByteArray(byte[] config) {
        return TestConfig.fromJson(config);
    }

    private static TestConfig fromController() {
        ControllerHttpClient controller = new ai.vespa.hosted.auth.ApiAuthenticator().controller();
        ApplicationId id = Properties.application();
        Environment environment = Properties.environment().orElse(Environment.dev);
        ZoneId zone = Properties.region().map(region -> ZoneId.from(environment, region))
                .orElseGet(() -> controller.defaultZone(environment));
        return controller.testConfig(id, zone);
    }
}
