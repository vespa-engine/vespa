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
    public sealed interface Action permits HttpGetAction, ExecAction {}

    public record HttpGetAction(String path, int port) implements Action {}

    public record ExecAction(List<String> command) implements Action {}
}
