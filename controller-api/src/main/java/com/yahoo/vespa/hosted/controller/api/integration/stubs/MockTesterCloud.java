package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;

import java.net.URI;
import java.util.Arrays;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Status.NOT_STARTED;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Status.RUNNING;

public class MockTesterCloud implements TesterCloud {

    private byte[] logs = new byte[0];
    private Status status = NOT_STARTED;
    private byte[] config;
    private URI testerUrl;

    @Override
    public void startTests(URI testerUrl, Suite suite, byte[] config) {
        this.status = RUNNING;
        this.config = config;
        this.testerUrl = testerUrl;
    }

    @Override
    public byte[] getLogs(URI testerUrl) {
        return Arrays.copyOf(logs, logs.length);
    }

    @Override
    public Status getStatus(URI testerUrl) {
        return status;
    }

    public void set(byte[] logs, Status status) {
        this.logs = Arrays.copyOf(logs, logs.length);
        this.status = status;
    }

    public byte[] config() {
        return config;
    }

    public URI testerUrl() {
        return testerUrl;
    }

}
