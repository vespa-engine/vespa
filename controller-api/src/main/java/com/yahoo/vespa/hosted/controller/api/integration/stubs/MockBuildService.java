// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jvenstad
 */
public class MockBuildService extends AbstractComponent implements BuildService {

    private final List<BuildJob> jobs = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void trigger(BuildJob buildJob) {
        jobs.add(buildJob);
    }

    @Override
    public boolean isRunning(BuildJob buildJob) {
        return jobs.contains(buildJob);
    }

    /** List all running jobs. */
    public List<BuildJob> jobs() {
        return new ArrayList<>(jobs);
    }

    /** Clears all running jobs. */
    public void clear() {
        jobs.clear();
    }

    /** Removes the given job for the given project and returns whether it was found. */
    public boolean remove(BuildJob buildJob) {
        return jobs.remove(buildJob);
    }

}
