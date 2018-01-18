// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintainer;

import com.yahoo.log.LogSetup;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Type;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.config.SlimeUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author freva
 */
public class Maintainer {

    private static final CoreCollector coreCollector = new CoreCollector(new ProcessExecuter());
    private static final HttpClient httpClient = createHttpClient(Duration.ofSeconds(5));

    public static void main(String[] args) {
        LogSetup.initVespaLogging("node-maintainer");
        if (args.length != 1) {
            throw new RuntimeException("Expected only 1 argument - a JSON list of maintainer jobs to execute");
        }

        Inspector object = SlimeUtils.jsonToSlime(args[0].getBytes()).get();
        if (object.type() != Type.ARRAY) {
            throw new IllegalArgumentException("Expected a list of maintainer jobs to execute");
        }

        // Variable must be effectively final to be used in lambda expression
        AtomicInteger numberOfJobsFailed = new AtomicInteger(0);
        object.traverse((ArrayTraverser) (int i, Inspector item) -> {
            try {
                String type = getFieldOrFail(item, "type").asString();
                Inspector arguments = getFieldOrFail(item, "arguments");
                parseMaintenanceJob(type, arguments);
            } catch (Exception e) {
                System.err.println("Failed executing job: " + item.toString());
                e.printStackTrace();
                numberOfJobsFailed.incrementAndGet();
            }
        });

        if (numberOfJobsFailed.get() > 0) {
            System.err.println(numberOfJobsFailed.get() + " of jobs has failed");
            System.exit(1);
        }
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
        Optional<Path> installStatePath = SlimeUtils.optionalString(arguments.field("yinstStatePath")).map(Paths::get);
        String feedEndpoint = getFieldOrFail(arguments, "feedEndpoint").asString();

        try {
            CoredumpHandler coredumpHandler = new CoredumpHandler(httpClient, coreCollector, coredumpsPath,
                                                                  doneCoredumpsPath, attributesMap, installStatePath,
                                                                  feedEndpoint);
            coredumpHandler.processAll();
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

    private static HttpClient createHttpClient(Duration timeout) {
        int timeoutInMillis = (int) timeout.toMillis();
        return HttpClientBuilder.create()
                .setUserAgent("node-maintainer")
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(timeoutInMillis)
                        .setConnectionRequestTimeout(timeoutInMillis)
                        .setSocketTimeout(timeoutInMillis)
                        .build())
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(10)
                .build();
    }

}
