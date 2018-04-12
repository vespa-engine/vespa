package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.BuildService;

public class MockBuildService implements BuildService {

    @Override
    public boolean trigger(BuildJob buildJob) {
        return true;
    }

    @Override
    public boolean isRunning(BuildJob buildJob) {
        return false;
    }

}
