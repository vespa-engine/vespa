// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeagent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum NodeAgentTask {

    // The full task name is prefixed with 'node>', e.g. 'node>DiskCleanup'
    DiskCleanup,
    CoreDumps,
    CredentialsMaintainer,
    AclMaintainer;

    private static final Map<String, NodeAgentTask> tasksByName = Arrays.stream(NodeAgentTask.values())
            .collect(Collectors.toUnmodifiableMap(NodeAgentTask::taskName, n -> n));

    private final String taskName;
    NodeAgentTask() {
        this.taskName = "node>" + name();
    }

    public String taskName() { return taskName; }

    public static Set<NodeAgentTask> fromString(List<String> tasks) {
        return tasks.stream().filter(tasksByName::containsKey).map(tasksByName::get).collect(Collectors.toUnmodifiableSet());
    }
}
