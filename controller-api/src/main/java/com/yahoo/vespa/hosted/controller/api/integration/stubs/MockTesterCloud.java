// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import ai.vespa.http.DomainName;
import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.api.integration.dns.RecordName;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Status.NOT_STARTED;
import static com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud.Status.RUNNING;

public class MockTesterCloud implements TesterCloud {

    private final NameService nameService;

    private List<LogEntry> log = new ArrayList<>();
    private Status status = NOT_STARTED;
    private byte[] config;
    private Optional<TestReport> testReport = Optional.empty();

    public MockTesterCloud(NameService nameService) {
        this.nameService = nameService;
    }

    @Override
    public void startTests(DeploymentId deploymentId, Suite suite, byte[] config) {
        this.status = RUNNING;
        this.config = config;
    }

    @Override
    public List<LogEntry> getLog(DeploymentId deploymentId, long after) {
        return log.stream().filter(entry -> entry.id() > after).collect(Collectors.toList());
    }

    @Override
    public Status getStatus(DeploymentId deploymentId) { return status; }

    @Override
    public boolean ready(URI testerUrl) {
        return true;
    }

    @Override
    public boolean testerReady(DeploymentId deploymentId) {
        return true;
    }

    @Override
    public Optional<InetAddress> resolveHostName(DomainName hostname) {
        return nameService.findRecords(Record.Type.A, RecordName.from(hostname.value())).stream()
                .findFirst()
                .map(record -> InetAddresses.forString(record.data().asString()))
                .or(() -> Optional.of(InetAddresses.forString("1.2.3.4")));
    }

    @Override
    public Optional<DomainName> resolveCname(DomainName hostName) {
        return nameService.findRecords(Record.Type.CNAME, RecordName.from(hostName.value())).stream()
                          .findFirst()
                          .map(record -> DomainName.of(record.data().asString().substring(0, record.data().asString().length() - 1)));
    }

    @Override
    public Optional<TestReport> getTestReport(DeploymentId deploymentId) {
        return testReport;
    }

    public void testReport(TestReport testReport) {
        this.testReport = Optional.ofNullable(testReport);
    }

    public void add(LogEntry entry) {
        log.add(entry);
    }

    public void clearLog() {
        log.clear();
    }

    public void set(Status status) {
        this.status = status;
    }

    public byte[] config() {
        return config;
    }

}
