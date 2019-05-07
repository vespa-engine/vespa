package ai.vespa.hosted.api;


/**
 * Contains information about the result of a {@link Deployment} against a {@link ControllerHttpClient}.
 *
 * @author jonmv
 */
public class DeploymentResult {

    private final String json; // TODO probably do this properly.

    public DeploymentResult(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }

}
