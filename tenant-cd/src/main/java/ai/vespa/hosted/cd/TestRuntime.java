package ai.vespa.hosted.cd;

import ai.vespa.hosted.api.Authenticator;
import ai.vespa.hosted.api.ControllerHttpClient;
import ai.vespa.hosted.api.Properties;
import ai.vespa.hosted.api.TestConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.zone.ZoneId;

import java.nio.file.Files;
import java.nio.file.Paths;

import static ai.vespa.hosted.api.TestConfig.fromJson;

/**
 * The place to obtain environment-dependent configuration for test of a Vespa deployment.
 *
 * @author jvenstad
 */
public class TestRuntime {

    private static TestRuntime theRuntime;

    private final TestConfig config;
    private final Authenticator authenticator;

    private TestRuntime(TestConfig config, Authenticator authenticator) {
        this.config = config;
        this.authenticator = authenticator;
    }

    /**
     * Returns the config for this test, or null if it has not been provided.
     *
     * If the system property {@code "vespa.test.config"} is set (to a file path), a file at that location
     * is attempted read, and config parsed from it.
     * Otherwise, config is fetched over HTTP from the hosted Vespa API, assuming the deployment indicated
     * by the optional {@code "environment"} and {@code "region"} system properties exists.
     * When environment is not specified, it defaults to {@link Environment#dev},
     * while region must be set unless the environment is {@link Environment#dev} or {@link Environment#perf}.
     */
    public static synchronized TestRuntime get() {
        if (theRuntime == null) {
            String configPath = System.getProperty("vespa.test.config");
            Authenticator authenticator = new ai.vespa.hosted.auth.Authenticator();
            theRuntime = new TestRuntime(configPath != null ? fromFile(configPath) : fromController(authenticator),
                                         authenticator);
        }
        return theRuntime;
    }

    /** Returns a copy of this runtime, with the given authenticator. */
    public TestRuntime with(Authenticator authenticator) {
        return new TestRuntime(config, authenticator);
    }

    private static TestConfig fromFile(String path) {
        try {
            return TestConfig.fromJson(Files.readAllBytes(Paths.get(path)));
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed reading config from '" + path + "'!", e);
        }
    }

    private static TestConfig fromController(Authenticator authenticator) {
        ControllerHttpClient controller = authenticator.controller();
        ApplicationId id = Properties.application();
        Environment environment = Properties.environment().orElse(Environment.dev);
        ZoneId zone = Properties.region().map(region -> ZoneId.from(environment, region))
                                .orElseGet(() -> controller.defaultZone(environment));
        return controller.testConfig(id, zone);
    }

}
