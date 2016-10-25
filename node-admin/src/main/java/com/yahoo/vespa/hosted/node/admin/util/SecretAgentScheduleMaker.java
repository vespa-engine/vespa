// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper class to generate and write the secret-agent schedule file.
 *
 * @author valerijf
 */
public class SecretAgentScheduleMaker {
    private final String id;
    private final int interval;
    private final Path checkExecuteable;
    private final String[] arguments;
    private String user = "nobody";
    private final Map<String, Object> tags = new LinkedHashMap<>();

    public SecretAgentScheduleMaker(String id, int interval, Path checkExecuteable, String... arguments) {
        this.id = id;
        this.interval = interval;
        this.checkExecuteable = checkExecuteable;
        this.arguments = arguments;
    }

    public SecretAgentScheduleMaker withRunAsUser(String user) {
        this.user = user;
        return this;
    }

    public SecretAgentScheduleMaker withTag(String tagKey, Object tagValue) {
        tags.put(tagKey, tagValue);
        return this;
    }

    public void writeTo(Path yamasAgentDirectory) throws IOException {
        Path scheduleFilePath = yamasAgentDirectory.resolve(id + ".yaml");
        Files.write(scheduleFilePath, toString().getBytes());
        scheduleFilePath.toFile().setReadable(true, false); // Give everyone read access to the schedule file
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder()
                .append("- id: ").append(id).append("\n")
                .append("  interval: ").append(interval).append("\n")
                .append("  user: ").append(user).append("\n")
                .append("  check: ").append(checkExecuteable.toFile()).append("\n");

        if (arguments.length > 0) {
            stringBuilder.append("  args: \n");
            for (String arg : arguments) {
                stringBuilder.append("    - ").append(arg).append("\n");
            }
        }

        if (!tags.isEmpty()) stringBuilder.append("  tags:\n");
        tags.forEach((key, value) -> stringBuilder.append("    ").append(key).append(": ").append(value).append("\n"));

        return stringBuilder.toString();
    }
}
