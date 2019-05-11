package ai.vespa.hosted.api;


import java.net.URI;

/**
 * Contains information about the result of a {@link Deployment} against a {@link ControllerHttpClient}.
 *
 * @author jonmv
 */
public class DeploymentResult {

    private final String message;
    private final URI location;

    public DeploymentResult(String message, URI location) {
        this.message = message;
        this.location = location;
    }

    public String message() {
        return message;
    }

    public URI location() {
        return location;
    }

}
