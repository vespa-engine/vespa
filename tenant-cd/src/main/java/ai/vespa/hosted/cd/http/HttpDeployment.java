package ai.vespa.hosted.cd.http;

import ai.vespa.hosted.api.Authenticator;
import ai.vespa.hosted.cd.TestDeployment;
import ai.vespa.hosted.cd.TestEndpoint;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;

import java.net.URI;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * A remote deployment of a Vespa application, reachable over HTTP. Contains {@link HttpEndpoint}s.
 *
 * @author jonmv
 */
public class HttpDeployment implements TestDeployment {

    private final ZoneId zone;
    private final Map<String, HttpEndpoint> endpoints;

    /** Creates a representation of the given deployment endpoints, using the authenticator for data plane access. */
    public HttpDeployment(Map<String, URI> endpoints, ZoneId zone, Authenticator authenticator) {
        this.zone = zone;
        this.endpoints = endpoints.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey(),
                                                      entry -> new HttpEndpoint(entry.getValue(), authenticator)));
    }

    @Override
    public TestEndpoint endpoint() {
        return endpoint("default");
    }

    @Override
    public TestEndpoint endpoint(String id) {
        if ( ! endpoints.containsKey(id))
            throw new NoSuchElementException("No cluster with id '" + id + "'");

        return endpoints.get(id);
    }

    @Override
    public TestDeployment asTestDeployment() {
        if (zone.environment() == Environment.prod)
            throw new IllegalArgumentException("Won't return a mutable view of a production deployment");

        return this;
    }

}
