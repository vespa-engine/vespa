// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.vespa.hosted.node.admin.container.ContainerOperations;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgentContext;
import com.yahoo.vespa.hosted.node.admin.task.util.file.UnixPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author bjorncs
 */
class PerfReportProducer extends AbstractProducer {

    public static String NAME = "perf-report";

    PerfReportProducer(ContainerOperations container) { super(container); }

    @Override public String name() { return NAME; }

    @Override
    public void produceArtifact(NodeAgentContext context, String configId, ServiceDumpReport.DumpOptions options,
                                UnixPath resultDirectoryInNode) throws IOException {
        int pid = findVespaServicePid(context, configId);
        int duration = (int) duration(context, options, 30.0);
        List<String> perfRecordCommand = new ArrayList<>(List.of("perf", "record"));
        if (options != null && Boolean.TRUE.equals(options.callGraphRecording())) {
            perfRecordCommand.add("-g");
        }
        String recordFile = resultDirectoryInNode.resolve("perf-record.bin").toString();
        perfRecordCommand.addAll(
                List.of("--output=" + recordFile,
                        "--pid=" + pid, "sleep", Integer.toString(duration)));
        executeCommand(context, perfRecordCommand, true);
        String perfReportFile = resultDirectoryInNode.resolve("perf-report.txt").toString();
        executeCommand(context, List.of("bash", "-c", "perf report --input=" + recordFile + " > " + perfReportFile), true);
    }
}
