// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd;

import ai.vespa.hosted.api.ControllerHttpClient;
import ai.vespa.hosted.api.Properties;
import ai.vespa.hosted.api.TestConfig;
import ai.vespa.hosted.cd.http.HttpDeployment;
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

    public VespaTestRuntime() {
            String configPath = System.getProperty("vespa.test.config");
            TestConfig config = configPath != null ? fromFile(configPath) : fromController();
            this.config = config;
            this.deploymentToTest = new HttpDeployment(config.deployments().get(config.zone()), new ai.vespa.hosted.auth.EndpointAuthenticator(config.system()));
    }

//    In use ?
//    /** Returns a copy of this runtime, with the given endpoint authenticator. */
//    public TestRuntime with(EndpointAuthenticator authenticator) {
//        return new TestRuntime(config, authenticator);
//    }

    /** Returns the full id of the application this is testing. */
    public ApplicationId application() { return config.application(); }

    /** Returns the zone of the deployment this is testing. */
    public ZoneId zone() { return config.zone(); }

    /** Returns the deployment this is testing. */
    @Override
    public Deployment deploymentToTest() { return deploymentToTest; }

    private static TestConfig fromFile(String path) {
        try {
            return TestConfig.fromJson(Files.readAllBytes(Paths.get(path)));
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed reading config from '" + path + "'!", e);
        }
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
