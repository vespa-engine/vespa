package com.yahoo.config.provision;

import java.util.List;

/**
 * Health check probe configuration for a docker/podman container, similar to Kubernetes probes.
 * Used for liveness, readiness and startup probes.
 *
 * @param action action (e.g. http request or command) to perform the health check.
 * @param initialDelaySeconds time to wait after container start before the first health check.
 * @param periodSeconds time between running the actions.
 * @param timeoutSeconds timeout for the action, the check fails if timeout is exceeded.
 * @param failureThreshold number of failed attempts after which the container will restart.
 * @author glebashnik
 */
public record Probe(
        Action action, int initialDelaySeconds, int periodSeconds, int timeoutSeconds, int failureThreshold) {

    public Probe {
        if (action == null) {
            throw new IllegalArgumentException("Probe action must not be null");
        }
        if (initialDelaySeconds < 0) {
            throw new IllegalArgumentException("initialDelaySeconds must be non-negative");
        }
        if (periodSeconds < 0) {
            throw new IllegalArgumentException("periodSeconds must be non-negative");
        }
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("timeoutSeconds must be non-negative");
        }
        if (failureThreshold < 0) {
            throw new IllegalArgumentException("failureThreshold must be non-negative");
        }
    }
    public sealed interface Action permits HttpGetAction, ExecAction {}

    public record HttpGetAction(String path, int port) implements Action {

        public HttpGetAction {
            if (path == null || path.isEmpty()) {
                throw new IllegalArgumentException("path must not be null or empty");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
        }
    }
    public record ExecAction(List<String> command) implements Action {
        public ExecAction {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("command must not be null or empty");
            }
        }
    }
}
