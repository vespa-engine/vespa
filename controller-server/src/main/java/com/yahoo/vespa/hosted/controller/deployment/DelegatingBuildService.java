package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.BuildService;

/**
 * Sends build jobs to an internal build system whenever it accepts them, or to an external one otherwise.
 *
 * @author jonmv
 */
public class DelegatingBuildService implements BuildService {

    private final BuildService external;
    private final BuildService internal;

    public DelegatingBuildService(BuildService external, BuildService internal) {
        this.external = external;
        this.internal = internal;
    }

    @Override
    public void trigger(BuildJob buildJob) {
        (internal.builds(buildJob) ? internal : external).trigger(buildJob);
    }

    @Override
    public JobState stateOf(BuildJob buildJob) {
        return (internal.builds(buildJob) ? internal : external).stateOf(buildJob);
    }

}
