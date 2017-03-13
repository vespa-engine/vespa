// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintainer;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Type;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.config.SlimeUtils;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author freva
 */
public class Maintainer {
    private static final CoreCollector coreCollector = new CoreCollector(new ProcessExecuter());
    private static final CoredumpHandler coredumpHandler = new CoredumpHandler(HttpClientBuilder.create().build(), coreCollector);

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new RuntimeException("Expected only 1 argument - a JSON list of maintainer jobs to execute");
        }

        Inspector object = SlimeUtils.jsonToSlime(args[0].getBytes()).get();
        if (object.type() != Type.ARRAY) {
            throw new IllegalArgumentException("Expected a list maintainer jobs to execute");
        }

        object.traverse((ArrayTraverser) (int i, Inspector item) -> {
            String type = getFieldOrFail(item, "type").asString();
            Inspector arguments = getFieldOrFail(item, "arguments");
            parseMaintenanceJob(type, arguments);
        });
    }

    private static void parseMaintenanceJob(String type, Inspector arguments) {
        if (arguments.type() != Type.OBJECT) {
            throw new IllegalArgumentException("Expected a 'arguments' to be an object");
        }

        switch (type) {
            case "delete-files":
                parseDeleteFilesJob(arguments);
                break;

            case "delete-directories":
                parseDeleteDirectoriesJob(arguments);
                break;

            case "recursive-delete":
                parseRecursiveDelete(arguments);
                break;

            case "move-files":
                parseMoveFiles(arguments);
                break;

            case "handle-core-dumps":
                parseHandleCoreDumps(arguments);
                break;

            default:
                throw new IllegalArgumentException("Unknown job: " + type);
        }
    }

    private static void parseDeleteFilesJob(Inspector arguments) {
        Path basePath = Paths.get(getFieldOrFail(arguments, "basePath").asString());
        Duration maxAge = Duration.ofSeconds(getFieldOrFail(arguments, "maxAgeSeconds").asLong());
        Optional<String> fileNameRegex = SlimeUtils.optionalString(arguments.field("fileNameRegex"));
        boolean recursive = getFieldOrFail(arguments, "recursive").asBool();
        try {
            FileHelper.deleteFiles(basePath, maxAge, fileNameRegex, recursive);
        } catch (IOException e) {
            throw new RuntimeException("Failed deleting files under " + basePath.toAbsolutePath() +
                    fileNameRegex.map(regex -> ", matching '" + regex + "'").orElse("") +
                    ", " + (recursive ? "" : "not ") + "recursively" +
                    " and older than " + maxAge, e);
        }
    }

    private static void parseDeleteDirectoriesJob(Inspector arguments) {
        Path basePath = Paths.get(getFieldOrFail(arguments, "basePath").asString());
        Duration maxAge = Duration.ofSeconds(getFieldOrFail(arguments, "maxAgeSeconds").asLong());
        Optional<String> dirNameRegex = SlimeUtils.optionalString(arguments.field("dirNameRegex"));
        try {
            FileHelper.deleteDirectories(basePath, maxAge, dirNameRegex);
        } catch (IOException e) {
            throw new RuntimeException("Failed deleting directories under " + basePath.toAbsolutePath() +
                    dirNameRegex.map(regex -> ", matching '" + regex + "'").orElse("") +
                    " and older than " + maxAge, e);
        }
    }

    private static void parseRecursiveDelete(Inspector arguments) {
        Path basePath = Paths.get(getFieldOrFail(arguments, "path").asString());
        try {
            FileHelper.recursiveDelete(basePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed deleting " + basePath.toAbsolutePath(), e);
        }
    }

    private static void parseMoveFiles(Inspector arguments) {
        Path from = Paths.get(getFieldOrFail(arguments, "from").asString());
        Path to = Paths.get(getFieldOrFail(arguments, "to").asString());

        try {
            FileHelper.moveIfExists(from, to);
        } catch (IOException e) {
            throw new RuntimeException("Failed moving from " + from.toAbsolutePath() + ", to " + to.toAbsolutePath(), e);
        }
    }

    private static void parseHandleCoreDumps(Inspector arguments) {
        Path coredumpsPath = Paths.get(getFieldOrFail(arguments, "coredumpsPath").asString());
        Path doneCoredumpsPath = Paths.get(getFieldOrFail(arguments, "doneCoredumpsPath").asString());
        Map<String, Object> attributesMap = parseMap(arguments);

        try {
            coredumpHandler.removeJavaCoredumps(coredumpsPath);
            coredumpHandler.processAndReportCoredumps(coredumpsPath, doneCoredumpsPath, attributesMap);
        } catch (IOException e) {
            throw new RuntimeException("Failed processing coredumps at " + coredumpsPath.toAbsolutePath() +
                    ", moving fished dumps to " + doneCoredumpsPath.toAbsolutePath(), e);
        }
    }

    private static Map<String, Object> parseMap(Inspector object) {
        Map<String, Object> map = new HashMap<>();
        getFieldOrFail(object, "attributes").traverse((String key, Inspector value) -> {
            switch (value.type()) {
                case BOOL:
                    map.put(key, value.asBool());
                    break;
                case LONG:
                    map.put(key, value.asLong());
                    break;
                case DOUBLE:
                    map.put(key, value.asDouble());
                    break;
                case STRING:
                    map.put(key, value.asString());
                    break;
                default:
                    throw new IllegalArgumentException("Invalid attribute for key '" + key + "', value " + value);
            }
        });
        return map;
    }

    private static Inspector getFieldOrFail(Inspector object, String key) {
        Inspector out = object.field(key);
        if (out.type() == Type.NIX) {
            throw new IllegalArgumentException("Key '" + key + "' was not found!");
        }
        return out;
    }
}
