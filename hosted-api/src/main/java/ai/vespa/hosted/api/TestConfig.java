// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.SlimeUtils;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final boolean isCI;
    private final String platform;
    private final long revision;
    private final Instant deployedAt;
    private final Map<ZoneId, Map<String, URI>> deployments;
    private final Map<ZoneId, List<String>> contentClusters;

    public TestConfig(ApplicationId application, ZoneId zone, SystemName system, boolean isCI,
                      String platform, long revision, Instant deployedAt,
                      Map<ZoneId, Map<String, URI>> deployments, Map<ZoneId, List<String>> contentClusters) {
        if ( ! deployments.containsKey(zone))
            throw new IllegalArgumentException("Config must contain a deployment for its zone, but only does for " + deployments.keySet());
        this.application = requireNonNull(application);
        this.zone = requireNonNull(zone);
        this.system = requireNonNull(system);
        this.isCI = isCI;
        this.platform = platform;
        this.revision = revision;
        this.deployedAt = deployedAt;
        this.deployments = deployments.entrySet().stream()
                                      .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                                            entry -> Map.copyOf(entry.getValue())));
        this.contentClusters = contentClusters.entrySet().stream()
                                              .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                                                    entry -> List.copyOf(entry.getValue())));
    }

    /**
     * Parses the given test config JSON and returns a new config instance.
     *
     * If the given JSON has a "localEndpoints" element, a config object with default values
     * is returned, using {@link #fromEndpointsOnly}. Otherwise, all config attributes are parsed.
     */
    public static TestConfig fromJson(byte[] jsonBytes) {
        Inspector config = SlimeUtils.jsonToSlime(jsonBytes).get();
        if (config.field("localEndpoints").valid())
            return TestConfig.fromEndpointsOnly(toClusterMap(config.field("localEndpoints")));

        if (config.field("deployments").valid()) {

        }

        ApplicationId application = ApplicationId.fromSerializedForm(config.field("application").asString());
        ZoneId zone = ZoneId.from(config.field("zone").asString());
        SystemName system = SystemName.from(config.field("system").asString());
        boolean isCI = config.field("isCI").asBool();
        String platform = config.field("platform").asString();
        long revision = config.field("revision").asLong();
        Instant deployedAt = Instant.ofEpochMilli(config.field("deployedAt").asLong());
        Map<ZoneId, Map<String, URI>> deployments = new HashMap<>();
        config.field("zoneEndpoints").traverse((ObjectTraverser) (zoneId, clustersObject) -> {
            deployments.put(ZoneId.from(zoneId), toClusterMap(clustersObject));
        });
        Map<ZoneId, List<String>> contentClusters = new HashMap<>();
        config.field("clusters").traverse(((ObjectTraverser) (zoneId, clustersArray) -> {
            List<String> clusters = new ArrayList<>();
            clustersArray.traverse((ArrayTraverser) (__, cluster) -> clusters.add(cluster.asString()));
            contentClusters.put(ZoneId.from(zoneId), clusters);
        }));
        return new TestConfig(application, zone, system, isCI, platform, revision, deployedAt, deployments, contentClusters);
    }

    static Map<String, URI> toClusterMap(Inspector clustersObject) {
        Map<String, URI> clusters = new HashMap<>();
        clustersObject.traverse((ObjectTraverser) (cluster, uri) -> clusters.put(cluster, URI.create(uri.asString())));
        return clusters;
    }

    /**
     * Returns a TestConfig with default values for everything except the endpoints.
     * @param endpoints the endpoint for each of the containers specified in services.xml, by container id
     */
    public static TestConfig fromEndpointsOnly(Map<String, URI> endpoints) {
        return new TestConfig(ApplicationId.defaultId(),
                              ZoneId.defaultId(),
                              SystemName.defaultSystem(),
                              false,
                              "",
                              0,
                              Instant.EPOCH,
                              Map.of(ZoneId.defaultId(), endpoints),
                              Map.of());
    }

    /** Returns the full id of the application to test. */
    public ApplicationId application() { return application; }

    /** Returns the zone of the deployment to test. */
    public ZoneId zone() { return zone; }

    /** Returns the Vespa cloud system this is run against. */
    public SystemName system() { return system; }

    /** Returns whether this is a CI job (or a local developer environment). */
    public boolean isCI() { return isCI; }

    /** Returns an immutable view of deployments, per zone, of the application to test. */
    public Map<ZoneId, Map<String, URI>> deployments() { return deployments; }

    /** Returns an immutable view of content clusters, per zone, of the application to test. */
    public Map<ZoneId, List<String>> contentClusters() { return contentClusters; }

    public String platformVersion() { return platform; }

    public long applicationVersion() { return revision; }

    public Instant deployedAt() { return deployedAt; }

}
