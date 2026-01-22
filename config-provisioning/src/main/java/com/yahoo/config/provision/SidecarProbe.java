package com.yahoo.config.provision;

import java.util.List;

/**
 * Health check probe configuration for a Sidecar container, similar to Kubernetes probes.
 *
 * @param action              action (e.g. http request or command) to perform the health check.
 * @param initialDelaySeconds time to wait after container start before the first health check.
 * @param periodSeconds       time between running the actions.
 * @param timeoutSeconds      timeout for the action, the check fails if timeout is exceeded.
 * @param failureThreshold    number of failed attempts after which the container is considered unhealthy.
 *                            
 * @author glebashnik
 */
public record SidecarProbe(
        Action action, int initialDelaySeconds, int periodSeconds, int timeoutSeconds, int failureThreshold) {

    /** 
     * Validates, sets time and attempt limits.
     * These are kept fairly low to detect unhealthy sidecars without a long wait.
     */
    public SidecarProbe {
        if (action == null) {
            throw new IllegalArgumentException("Probe action must not be null");
        }
        
        if (initialDelaySeconds < 0 || initialDelaySeconds > 300) {
            throw new IllegalArgumentException("initialDelaySeconds must be between 0 and 300 seconds, got: " + initialDelaySeconds);
        }
        if (periodSeconds < 1 || periodSeconds > 10) {
            throw new IllegalArgumentException(
                    "periodSeconds must be between 1 and 10 seconds, got: " + periodSeconds);
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 10) {
            throw new IllegalArgumentException(
                    "timeoutSeconds must be between 1 and 10 seconds, got: " + timeoutSeconds);
        }
        if (failureThreshold < 1 || failureThreshold > 3) {
            throw new IllegalArgumentException(
                    "failureThreshold must be between 1 and 3, got: " + failureThreshold);
        }
    }

    /**
     * Action to perform for the health check.
     */
    public sealed interface Action permits HttpGetAction, ExecAction {
    }

    /**
     * HTTP GET action for the health check probe.
     * Any response code greater than or equal to 200 and less than 400 indicates success.
     * Any other code indicates failure.
     *
     * @param path URL path, e.g. /v2/health/live
     * @param port port number
     */
    public record HttpGetAction(String path, int port) implements Action {
        public HttpGetAction {
            if (path == null || path.isEmpty() || path.charAt(0) != '/') {
                throw new IllegalArgumentException(
                        "Path must not be null or empty and must start with '/', got: " + path);
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535, got: " + port);
            }
        }
    }

    /**
     * Exec action for the health check probe.
     * The command is executed inside the container.
     * If the command exits with a status code of 0, the probe is considered successful.
     * Any other exit code indicates failure.
     *
     * @param command list of command and its arguments
     */
    public record ExecAction(List<String> command) implements Action {
        public ExecAction {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("Command must not be null or empty");
            }
            
            // For immutability
            command = List.copyOf(command);
        }
    }
}
