// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;


/**
 * Contains information about the result of a {@link Deployment} against a {@link ControllerHttpClient}.
 *
 * @author jonmv
 */
public class DeploymentResult {

    private final String message;
    private final long run;

    public DeploymentResult(String message, long run) {
        this.message = message;
        this.run = run;
    }

    public String message() {
        return message;
    }

    public long run() {
        return run;
    }

}
