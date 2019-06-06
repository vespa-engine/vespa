package ai.vespa.hosted.cd;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Slime;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The place to obtain environment-dependent configuration for the current test run.
 *
 * If the system property 'vespa.test.config' is set, this class attempts to parse config
 * from a JSON file at that location -- otherwise, attempts to access the config will return null.
 *
 * @author jvenstad
 */
public class TestConfig {

    private static final TestConfig theConfig = fromFile(Paths.get(System.getProperty("vespa.test.config")));

    private final ApplicationId application;
    private final ZoneId zone;
    private final SystemName system;
    private final Map<ZoneId, Deployment> deployments;

    private TestConfig(ApplicationId application, ZoneId zone, SystemName system, Map<ZoneId, Deployment> deployments) {
        this.application = application;
        this.zone = zone;
        this.system = system;
        this.deployments = Map.copyOf(deployments);
    }

    /** Returns the config for this test, or null if it has not been provided. */
    public static TestConfig get() { return theConfig; }

    /** Returns the full id of the application to be tested. */
    public ApplicationId application() { return application; }

    /** Returns the zone of the deployment to test. */
    public ZoneId zone() { return zone; }

    /** Returns an immutable view of all configured endpoints for each zone of the application to test. */
    public Map<ZoneId, Deployment> allDeployments() { return deployments; }

    /** Returns the deployment to test in this test runtime. */
    public Deployment deploymentToTest() { return deployments.get(zone); }

    /** Returns the system this is run against. */
    SystemName system() { return system; }

    static TestConfig fromFile(Path path) {
        if (path == null)
            return null;

        try {
            Inspector config = new JsonDecoder().decode(new Slime(), Files.readAllBytes(path)).get();
            ApplicationId application = ApplicationId.fromSerializedForm(config.field("application").asString());
            ZoneId zone = ZoneId.from(config.field("zone").asString());
            SystemName system = SystemName.from(config.field("system").asString());
            Map<ZoneId, Deployment> endpoints = new HashMap<>();
            config.field("endpoints").traverse((ObjectTraverser) (zoneId, endpointArray) -> {
                List<URI> uris = new ArrayList<>();
                endpointArray.traverse((ArrayTraverser) (__, uri) -> uris.add(URI.create(uri.asString())));
                endpoints.put(ZoneId.from(zoneId), null); // TODO jvenstad
            });
            return new TestConfig(application, zone, system, endpoints);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed reading config from '" + path + "'!", e);
        }
    }

}
