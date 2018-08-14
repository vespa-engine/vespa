package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.NotExistsException;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;
import com.yahoo.vespa.hosted.controller.deployment.JobController;
import com.yahoo.vespa.hosted.controller.deployment.RunDetails;
import com.yahoo.vespa.hosted.controller.deployment.Run;
import com.yahoo.vespa.hosted.controller.deployment.Step;
import com.yahoo.vespa.hosted.controller.restapi.SlimeJsonResponse;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implements the REST API for the job controller delegated from the Application API.
 *
 * @see JobController
 * @see ApplicationApiHandler
 */
class JobControllerApiHandlerHelper {

    /**
     * @return Response with all job types that have recorded runs for the application _and_ the status for the last run of that type
     */
    static HttpResponse jobTypeResponse(List<JobType> sortedJobs, Map<JobType, Run> lastRun, URI baseUriForJobs) {
        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();
        Cursor jobArray = responseObject.setArray("jobs");

        sortedJobs.forEach(jobType ->
                jobTypeToSlime(jobArray.addObject(), jobType, Optional.ofNullable(lastRun.get(jobType)), baseUriForJobs));
        return new SlimeJsonResponse(slime);
    }

    private static void jobTypeToSlime(Cursor cursor, JobType jobType, Optional<Run> lastRun, URI baseUriForJobs) {
        Cursor jobObject = cursor.setObject(jobType.jobName());

        // Url that are specific to the jobtype
        String jobTypePath = baseUriForJobs.getPath() + "/" + jobType.jobName();
        URI baseUriForJobType = baseUriForJobs.resolve(jobTypePath);
        jobObject.setString("url", baseUriForJobType.toString());

        // Add the last run for the jobtype if present
        lastRun.ifPresent(run -> {
            Cursor lastObject = jobObject.setObject("last");
            runToSlime(lastObject, run, baseUriForJobType);
        });
    }

    /**
     * @return Response with the runs for a specific jobtype
     */
    static HttpResponse runResponse(Map<RunId, Run> runs, URI baseUriForJobType) {
        Slime slime = new Slime();
        Cursor cursor = slime.setObject();

        runs.forEach((runid, run) -> runToSlime(cursor.setObject(Long.toString(runid.number())), run, baseUriForJobType));

        return new SlimeJsonResponse(slime);
    }

    private static void runToSlime(Cursor cursor, Run run, URI baseUriForJobType) {
        cursor.setString("status", run.status().name());
        run.end().ifPresent(instant -> cursor.setString("end", instant.toString()));

        Cursor stepsArray = cursor.setArray("steps");
        run.steps().forEach((step, status) -> {
            Cursor stepObject = stepsArray.addObject();
            stepObject.setString(step.name(), status.name());
        });

        cursor.setString("start", run.start().toString());
        cursor.setLong("id", run.id().number());
        String logsPath = baseUriForJobType.getPath() + "/run/" + run.id().number();
        cursor.setString("logs", baseUriForJobType.resolve(logsPath).toString());
    }

    /**
     * @return Response with logs from a single run
     */
    static HttpResponse runDetailsResponse(JobController jobController, RunId runId) {
        Slime slime = new Slime();
        Cursor logsObject = slime.setObject();

        RunDetails runDetails = jobController.details(runId).orElseThrow(() ->
                new NotExistsException(String.format(
                        "No run details exist for application: %s, job type: %s, number: %d",
                        runId.application().toShortString(), runId.type().jobName(), runId.number())));
        for (Step step : Step.values()) {
            runDetails.get(step).ifPresent(stepLog -> logsObject.setString(step.name(), stepLog));
        }

        return new SlimeJsonResponse(slime);
    }

    /**
     * Unpack payload and submit to job controller. Defaults instance to 'default' and renders the
     * application version on success.
     *
     * @return Response with the new application version
     */
    static HttpResponse submitResponse(JobController jobController, String tenant, String application,
                                       SourceRevision sourceRevision, byte[] appPackage, byte[] testPackage) {
        ApplicationVersion version = jobController.submit(ApplicationId.from(tenant, application, "default"),
                sourceRevision, appPackage, testPackage);

        Slime slime = new Slime();
        Cursor responseObject = slime.setObject();
        responseObject.setString("version", version.id());
        return new SlimeJsonResponse(slime);
    }
}

