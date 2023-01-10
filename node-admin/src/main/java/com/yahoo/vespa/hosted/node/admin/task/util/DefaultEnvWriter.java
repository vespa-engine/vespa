// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util;

import com.yahoo.vespa.hosted.node.admin.component.TaskContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.node.admin.task.util.file.IOExceptionUtil.ifExists;
import static com.yahoo.yolean.Exceptions.uncheck;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.util.stream.Collectors.joining;

/**
 * Rewrites default-env.txt files.
 *
 * @author bjorncs
 */
public class DefaultEnvWriter {

    private static final Logger logger = Logger.getLogger(DefaultEnvWriter.class.getName());

    private final Map<String, Operation> operations = new LinkedHashMap<>();

    public DefaultEnvWriter addOverride(String name, String value) {
        return addOperation("override", name, value);
    }

    public DefaultEnvWriter addFallback(String name, String value) {
        return addOperation("fallback", name, value);
    }

    public DefaultEnvWriter addUnset(String name) {
        return addOperation("unset", name, null);
    }

    private DefaultEnvWriter addOperation(String action, String name, String value) {
        if (operations.containsKey(name)) {
            throw new IllegalArgumentException(String.format("Operation on variable '%s' already added", name));
        }
        operations.put(name, new Operation(action, name, value));
        return this;
    }

    /**
     * Updates or created a default-env.txt file
     *
     * @return true if the file was modified
     */
    public boolean updateFile(TaskContext context, Path defaultEnvFile) {
        List<String> currentDefaultEnvLines = ifExists(() -> Files.readAllLines(defaultEnvFile)).orElse(List.of());
        List<String> newDefaultEnvLines = generateContent(currentDefaultEnvLines);
        if (currentDefaultEnvLines.equals(newDefaultEnvLines)) {
            return false;
        } else {
            context.log(logger, "Updating " + defaultEnvFile.toString());
            Path tempFile = defaultEnvFile.resolveSibling(defaultEnvFile.getFileName() + ".tmp");
            uncheck(() -> Files.write(tempFile, newDefaultEnvLines));
            uncheck(() -> Files.move(tempFile, defaultEnvFile, ATOMIC_MOVE));
            return true;
        }
    }

    /**
     * @return generated default-env.txt content
     */
    public String generateContent() {
        return generateContent(List.of()).stream()
                .collect(joining(System.lineSeparator(), "", System.lineSeparator()));
    }

    private List<String> generateContent(List<String> currentDefaultEnvLines) {
        List<String> newDefaultEnvLines = new ArrayList<>();
        Set<String> seenNames = new TreeSet<>();
        for (String line : currentDefaultEnvLines) {
            String[] items = line.split(" ");
            if (items.length < 2) {
                throw new IllegalArgumentException(String.format("Invalid line in file '%s': %s", currentDefaultEnvLines, line));
            }
            String name = items[1];
            if (!seenNames.contains(name)) { // implicitly removes duplicated variables
                seenNames.add(name);
                Operation operation = operations.get(name);
                if (operation != null) {
                    newDefaultEnvLines.add(operation.toLine());
                } else {
                    newDefaultEnvLines.add(line);
                }
            }
        }
        for (var operation : operations.values()) {
            if (!seenNames.contains(operation.name)) {
                newDefaultEnvLines.add(operation.toLine());
            }
        }
        return newDefaultEnvLines;
    }

    private record Operation(String action, String name, String value) {
        String toLine() {
            if (action.equals("unset")) {
                return "unset " + name;
            }
            return action + " " + name + " " + value;
        }
    }
}


