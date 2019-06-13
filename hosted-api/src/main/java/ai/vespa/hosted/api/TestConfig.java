package ai.vespa.hosted.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonDecoder;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Config required to run a functional or verification test of a Vespa deployment.
 *
 * @author jvenstad
 */
public class TestConfig {

    private final ApplicationId application;
    private final ZoneId zone;
    private final SystemName system;
    private final Map<ZoneId, Map<String, URI>> deployments;

    public TestConfig(ApplicationId application, ZoneId zone, SystemName system, Map<ZoneId, Map<String, URI>> deployments) {
        if ( ! deployments.containsKey(zone))
            throw new IllegalArgumentException("Config must contain a deployment for its zone, but only does for " + deployments.keySet());
        this.application = requireNonNull(application);
        this.zone = requireNonNull(zone);
        this.system = requireNonNull(system);
        this.deployments = deployments.entrySet().stream()
                                      .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                                            entry -> Map.copyOf(entry.getValue())));
    }

    public static TestConfig fromJson(byte[] jsonBytes) {
        Inspector config = new JsonDecoder().decode(new Slime(), jsonBytes).get();
        ApplicationId application = ApplicationId.fromSerializedForm(config.field("application").asString());
        ZoneId zone = ZoneId.from(config.field("zone").asString());
        SystemName system = SystemName.from(config.field("system").asString());
        Map<ZoneId, Map<String, URI>> deployments = new HashMap<>();
        config.field("clusterEndpoints").traverse((ObjectTraverser) (zoneId, endpointsObject) -> {
            Map<String, URI> endpoints = new HashMap<>();
            endpointsObject.traverse((ObjectTraverser) (cluster, uri) -> endpoints.put(cluster, URI.create(uri.asString())));
            deployments.put(ZoneId.from(zoneId), endpoints);
        });
        return new TestConfig(application, zone, system, deployments);
    }

    /** Returns the full id of the application to test. */
    public ApplicationId application() { return application; }

    /** Returns the zone of the deployment to test. */
    public ZoneId zone() { return zone; }

    /** Returns an immutable view of deployments, per zone, of the application to test. */
    public Map<ZoneId, Map<String, URI>> deployments() { return deployments; }

    /** Returns the hosted Vespa system this is run against. */
    public SystemName system() { return system; }

}
