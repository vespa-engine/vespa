package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockLogStore;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.deployment.Versions;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.fail;

public class JobControllerApiHandlerHelperTest {

    private final ApplicationId appId = ApplicationId.from("vespa", "music", "default");
    private final Instant start = Instant.parse("2018-06-27T10:12:35Z");
    static final Versions versions = new Versions(Version.fromString("1.2.3"),
                                                  ApplicationVersion.from(new SourceRevision("repo",
                                                                                             "branch",
                                                                                             "bada55"),
                                                                          321),
                                                  Optional.empty(),
                                                  Optional.empty());

    private static Step lastStep = Step.values()[Step.values().length - 1];

    @Test
    public void jobTypeResponse() {
        Map<JobType, RunStatus> jobMap = new HashMap<>();
        List<JobType> jobList = new ArrayList<>();
        jobMap.put(JobType.systemTest, createStatus(JobType.systemTest, 1, 30, lastStep, Step.Status.succeeded));
        jobList.add(JobType.systemTest);
        jobMap.put(JobType.productionApNortheast1, createStatus(JobType.productionApNortheast1, 1, 60, lastStep, Step.Status.succeeded));
        jobList.add(JobType.productionApNortheast1);
        jobMap.put(JobType.productionUsWest1, createStatus(JobType.productionUsWest1, 1, 60, Step.startTests, Step.Status.failed));
        jobList.add(JobType.productionUsWest1);

        URI jobUrl = URI.create("https://domain.tld/application/v4/tenant/sometenant/application/someapp/instance/usuallydefault/job");

        HttpResponse response = JobControllerApiHandlerHelper.jobTypeResponse(jobList, jobMap, jobUrl);
        assertFile(response, "job/job-type-response.json");
    }

    @Test
    public void runStatusResponse() {
        Map<RunId, RunStatus> statusMap = new HashMap<>();
        RunStatus status;

        status = createStatus(JobType.systemTest, 3, 30, lastStep, Step.Status.succeeded);
        statusMap.put(status.id(), status);

        status = createStatus(JobType.systemTest, 2, 56, Step.installReal, Step.Status.failed);
        statusMap.put(status.id(), status);

        status = createStatus(JobType.systemTest, 1, 44, lastStep, Step.Status.succeeded);
        statusMap.put(status.id(), status);

        URI jobTypeUrl = URI.create("https://domain.tld/application/v4/tenant/sometenant/application/someapp/instance/usuallydefault/job/systemtest");

        HttpResponse response = JobControllerApiHandlerHelper.runStatusResponse(statusMap, jobTypeUrl);
        assertFile(response, "job/run-status-response.json");
    }

    @Test
    public void runDetailsResponse() {
        ControllerTester tester = new ControllerTester();
        MockLogStore logStore = new MockLogStore();
        JobController jobController = new JobController(tester.controller(), logStore);
        RunId runId = new RunId(appId, JobType.systemTest, 42);
        tester.curator().writeHistoricRuns(
                runId.application(),
                runId.type(),
                Collections.singleton(createStatus(JobType.systemTest, 42, 44, lastStep, Step.Status.succeeded)));

        logStore.append(runId, Step.deployTester.name(), "INFO\t1234567890\tSUCCESS".getBytes());
        logStore.append(runId, Step.installTester.name(), "INFO\t1234598760\tSUCCESS".getBytes());
        logStore.append(runId, Step.deactivateTester.name(), "INFO\t1234678901\tERROR: Something went wrong".getBytes());

        HttpResponse response = JobControllerApiHandlerHelper.runDetailsResponse(jobController, runId);
        assertFile(response, "job/run-details-response.json");
    }

    @Test
    public void submitResponse() {
        ControllerTester tester = new ControllerTester();
        tester.createTenant("tenant", "domain", 1L);
        tester.createApplication(TenantName.from("tenant"), "application", "default", 1L);

        JobController jobController = new JobController(tester.controller(), new MockLogStore());
        jobController.register(ApplicationId.from("tenant", "application", "default"));

        HttpResponse response = JobControllerApiHandlerHelper.submitResponse(
                jobController, "tenant", "application", new SourceRevision("repository", "branch", "commit"), new byte[0], new byte[0]);
        compare(response, "{\"version\":\"1.0.1-commit\"}");
    }


    private RunStatus createStatus(JobType type, long runid, long duration, Step lastStep, Step.Status lastStepStatus) {
        RunId runId = new RunId(appId, type, runid);

        Map<Step, Step.Status> stepStatusMap = new HashMap<>();
        for (Step step : Step.values()) {
            if (step.ordinal() < lastStep.ordinal()) {
                stepStatusMap.put(step, Step.Status.succeeded);
            } else if (step.equals(lastStep)) {
                stepStatusMap.put(step, lastStepStatus);
            } else {
                stepStatusMap.put(step, Step.Status.unfinished);
            }
        }

        Optional<Instant> end = Optional.empty();
        if (lastStepStatus == Step.Status.failed || stepStatusMap.get(lastStep) == Step.Status.succeeded) {
            end = Optional.of(start.plusSeconds(duration));
        }

        return new RunStatus(runId, stepStatusMap, versions, start, end, false);
    }

    private void compare(HttpResponse response, String expected) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            response.render(baos);
            String actual = new String(baos.toByteArray());

            JSONObject actualJSON = new JSONObject(actual);
            JSONObject expectedJSON = new JSONObject(expected);
            Assert.assertEquals(expectedJSON.toString(), actualJSON.toString());
        } catch (IOException | JSONException e) {
            fail();
        }
    }

    private void assertFile(HttpResponse response, String resourceName) {
        try {
            Path path = Paths.get(getClass().getClassLoader().getResource(resourceName).getPath());
            String expected = new String(Files.readAllBytes(path));
            compare(response, expected);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
