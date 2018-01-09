// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.api.Model;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Version;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.ServerCache;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.monitoring.MetricUpdater;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

import static com.yahoo.vespa.config.server.application.FileDistributionStatus.HostStatus;
import static com.yahoo.vespa.config.server.application.FileDistributionStatus.Status;

/**
 * @author hmusum
 */
public class FileDistributionStatusTest {

    private final Duration timeout = Duration.ofMillis(100);

    private TenantName tenant = TenantName.from("mytenant");
    private ApplicationId appId = ApplicationId.from(tenant, ApplicationName.from("myapp"), InstanceName.from("myinstance"));
    private Application application;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void require_status_finished() throws IOException {
        Map<String, Double> fileReferenceStatuses = new HashMap<>();
        fileReferenceStatuses.put("1234", 1.0);
        FileDistributionStatus status = new MockStatus(statusFinished("localhost", Status.FINISHED, fileReferenceStatuses));
        application = createApplication("localhost");

        HttpResponse response = getStatus(status, application);
        assertResponse(200,
                       "{" +
                               "\"hosts\":[" +
                               "{\"hostname\":\"localhost\"," +
                               "\"status\":\"FINISHED\"," +
                               "\"fileReferences\":[" +
                               "{\"1234\":1.0}]}" +
                               "]," +
                               "\"status\":\"FINISHED\"}",
                       response);
    }

    @Test
    public void require_status_in_progress_one_host() throws IOException {
        Map<String, Double> fileReferenceStatuses = new HashMap<>();
        fileReferenceStatuses.put("1234", 0.2);
        FileDistributionStatus status = new MockStatus(statusWithError("localhost2", Status.IN_PROGRESS, fileReferenceStatuses, ""));
        application = createApplication("localhost2");

        HttpResponse response = getStatus(status, application);
        assertResponse(200,
                       "{" +
                               "\"hosts\":[" +
                               "{\"hostname\":\"localhost2\"," +
                               "\"status\":\"IN_PROGRESS\"," +
                               "\"message\":\"\"," +
                               "\"fileReferences\":[" +
                               "{\"1234\":0.2}]}" +
                               "]," +
                               "\"status\":\"IN_PROGRESS\"}",
                       response);
    }

    @Test
    public void require_different_statuses__many_hosts() throws IOException {
        application = createApplication("localhost", "localhost2");

        Map<String, Double> fileReferenceStatuses = new HashMap<>();
        fileReferenceStatuses.put("1234", 0.2);
        fileReferenceStatuses.put("abcd", 1.0);
        HostStatus localhost = statusWithError("localhost", Status.IN_PROGRESS, fileReferenceStatuses, "connection timed out");

        Map<String, Double> fileReferenceStatuses2 = new HashMap<>();
        fileReferenceStatuses2.put("1234", 1.0);
        HostStatus localhost2 = statusFinished("localhost2", Status.FINISHED, fileReferenceStatuses2);

        FileDistributionStatus status = new MockStatus(new HashSet<>(Arrays.asList(localhost, localhost2)));
        application = createApplication("localhost", "localhost2");
        HttpResponse response = getStatus(status, application);
        assertResponse(200,
                       "{" +
                               "\"hosts\":[" +
                               "{\"hostname\":\"localhost\"," +
                               "\"status\":\"IN_PROGRESS\"," +
                               "\"message\":\"connection timed out\"," +
                               "\"fileReferences\":[" +
                               "{\"1234\":0.2},{\"abcd\":1.0}]}," +
                               "{\"hostname\":\"localhost2\"," +
                               "\"status\":\"FINISHED\"," +
                               "\"fileReferences\":[" +
                               "{\"1234\":1.0}]}" +
                               "]," +
                               "\"status\":\"IN_PROGRESS\"}",
                       response);
    }

    private void assertResponse(int statusCode, String expectedResponse, HttpResponse response) throws IOException {
        assertEquals(statusCode, response.getStatus());
        assertEquals(expectedResponse, SessionHandlerTest.getRenderedString(response));
    }

    private HostStatus statusFinished(String hostname, Status status, Map<String, Double> fileReferenceStatuses) {
        return new HostStatus(hostname, status, fileReferenceStatuses);
    }

    private HostStatus statusWithError(String hostname,
                                       Status status,
                                       Map<String, Double> fileReferenceStatuses,
                                       String errorMessage) {
        return new HostStatus(hostname, status, fileReferenceStatuses, errorMessage);
    }

    private Application createApplication(String... hostname) {
        return createApplication(Arrays.asList(hostname));
    }

    private Application createApplication(List<String> hostnames) {
        Model mockModel = MockModel.createConfigProxies(hostnames, 1337);
        return new Application(mockModel, new ServerCache(), 3, Version.fromIntValues(0, 0, 0), MetricUpdater.createTestUpdater(), appId);
    }

    HttpResponse getStatus(FileDistributionStatus fileDistributionStatus, Application application) {
        return fileDistributionStatus.status(application, timeout);
    }

    private static class MockStatus extends FileDistributionStatus {

        private final Map<String, HostStatus> statuses = new HashMap<>();

        // host status to be returned in getHostStatus()
        MockStatus(HostStatus status) {
            this(Collections.singleton(status));
        }

        // host status per host to be returned in getHostStatus()
        MockStatus(Set<HostStatus> status) {
            status.forEach(s -> statuses.put(s.hostname(), s));
        }

        @Override
        HostStatus getHostStatus(String hostname, int port, Duration timeout) {
            return statuses.get(hostname);
        }
    }

}
