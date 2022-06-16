// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.cloud.impl;

import ai.vespa.cloud.Zone;
import ai.vespa.hosted.api.ControllerHttpClient;
import ai.vespa.hosted.api.DefaultApiAuthenticator;
import ai.vespa.hosted.cd.commons.DefaultEndpointAuthenticator;
import ai.vespa.hosted.api.Properties;
import ai.vespa.hosted.api.TestConfig;
import ai.vespa.hosted.cd.Deployment;
import ai.vespa.hosted.cd.TestRuntime;
import ai.vespa.hosted.cd.commons.HttpDeployment;
import ai.vespa.hosted.cd.commons.FeedClientBuilder;
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
        DefaultEndpointAuthenticator authenticator = new DefaultEndpointAuthenticator(config.system());
        this.deploymentToTest = new HttpDeployment(config.platformVersion(), config.applicationVersion(), config.deployedAt(),
                                                   config.deployments().get(config.zone()), authenticator);
        FeedClientBuilder.setEndpointAuthenticator(authenticator);
        ai.vespa.feed.client.FeedClientBuilder.setFeedClientBuilderSupplier(FeedClientBuilder::new);
    }

    @Override
    public Zone zone() {
        return new Zone(
                ai.vespa.cloud.Environment.valueOf(config.zone().environment().name()),
                config.zone().region().value());
    }

    @Override
    public ai.vespa.cloud.ApplicationId application() {
        return new ai.vespa.cloud.ApplicationId(config.application().tenant().value(),
                                                config.application().application().value(),
                                                config.application().instance().value());
    }

    /** Returns the deployment this is testing. */
    @Override
    public Deployment deploymentToTest() { return deploymentToTest; }

    private static TestConfig configFromPropertyOrController() {
        String configPath = System.getProperty("vespa.test.config");
        if (configPath != null) {
            System.out.println("TestRuntime: Using test config from " + configPath);
            return fromFile(configPath);
        }
        else {
            System.out.println("TestRuntime: Using test config from Vespa Cloud");
            return fromController();
        }
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
        ControllerHttpClient controller = new DefaultApiAuthenticator().controller();
        ApplicationId id = Properties.application();
        Environment environment = Properties.environment().orElse(Environment.dev);
        ZoneId zone = Properties.region().map(region -> ZoneId.from(environment, region))
                .orElseGet(() -> controller.defaultZone(environment));
        System.out.println("TestRuntime: Requesting endpoint config for tenant.application.instance: " + id.toFullString());
        System.out.println("TestRuntime: Zone: " + zone.toString());
        return controller.testConfig(id, zone);
    }

}
