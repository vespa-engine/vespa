// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintenance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.system.ProcessExecuter;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author freva
 */
public class Maintainer {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException("Expected only 1 argument - a JSON list of maintenance jobs to execute");
        }

        ObjectMapper mapper = new ObjectMapper();
        List<MaintenanceJob> maintenanceJobs = mapper.readValue(args[0], new TypeReference<List<MaintenanceJob>>(){});
        for (MaintenanceJob job : maintenanceJobs) {
            try {
                executeJob(job);
            } catch (Exception e) {
                throw new Exception("Failed to execute job " + job.jobName + " with arguments " +
                        Arrays.toString(job.arguments.entrySet().toArray()), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void executeJob(MaintenanceJob job) throws IOException {
        switch (job.getJobName()) {
            case "delete-files":
                DeleteOldAppData.deleteFiles(
                        (String) job.getRequiredArgument("basePath"),
                        (Integer) job.getRequiredArgument("maxAgeSeconds"),
                        (String) job.getArgumentOrDefault("fileNameRegex", null),
                        (boolean) job.getArgumentOrDefault("recursive", false));
                break;

            case "delete-directories":
                DeleteOldAppData.deleteDirectories(
                        (String) job.getRequiredArgument("basePath"),
                        (Integer) job.getRequiredArgument("maxAgeSeconds"),
                        (String) job.getArgumentOrDefault("dirNameRegex", null));
                break;

            case "recursive-delete":
                DeleteOldAppData.recursiveDelete(
                        (String) job.getRequiredArgument("path"));
                break;

            case "move-files":
                Path from = Paths.get((String) job.getRequiredArgument("from"));
                Path to = Paths.get((String) job.getRequiredArgument("to"));
                Files.move(from, to);
                break;

            case "handle-core-dumps":
                CoreCollector coreCollector = new CoreCollector(new ProcessExecuter());
                CoredumpHandler coredumpHandler = new CoredumpHandler(HttpClientBuilder.create().build(), coreCollector);

                Path containerCoredumpsPath = Paths.get((String) job.getRequiredArgument("containerCoredumpsPath"));
                Path doneCoredumpsPath = Paths.get((String) job.getRequiredArgument("doneCoredumpsPath"));
                Map<String, Object> attributesMap = (Map<String, Object>) job.getRequiredArgument("attributes");

                coredumpHandler.removeJavaCoredumps(containerCoredumpsPath);
                coredumpHandler.processAndReportCoredumps(containerCoredumpsPath, doneCoredumpsPath, attributesMap);
                break;

            default:
                throw new RuntimeException("Unknown job: " + job.getJobName());
        }
    }

    /**
     * Should be equal to MaintainerExecutorJob in StorageMaintainer
     */
    private static class MaintenanceJob {
        private final String jobName;
        private final Map<String, Object> arguments;

        private MaintenanceJob(@JsonProperty(value="jobName") String jobName,
                               @JsonProperty(value="arguments") Map<String, Object> arguments) {
            this.jobName = jobName;
            this.arguments = arguments;
        }

        String getJobName() {
            return jobName;
        }

        Object getRequiredArgument(String argumentName) {
            Object value = arguments.get(argumentName);
            if (value == null) {
                throw new IllegalArgumentException("Missing required argument " + argumentName);
            }
            return value;
        }

        Object getArgumentOrDefault(String argumentName, Object defaultValue) {
            return arguments.getOrDefault(argumentName, defaultValue);
        }
    }
}
