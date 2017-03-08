// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.maintainer.restapi.v1;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.system.ProcessExecuter;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.node.maintainer.CoreCollector;
import com.yahoo.vespa.hosted.node.maintainer.CoredumpHandler;
import com.yahoo.vespa.hosted.node.maintainer.DeleteOldAppData;
import com.yahoo.yolean.Exceptions;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.logging.Level;


/**
 * @author freva
 */
public class MaintainerApiHandler extends LoggingRequestHandler {
    private static final CoreCollector coreCollector = new CoreCollector(new ProcessExecuter());
    private static final CoredumpHandler coredumpHandler = new CoredumpHandler(HttpClientBuilder.create().build(), coreCollector);

    public MaintainerApiHandler(Executor executor, AccessLog accessLog) {
        super(executor, accessLog);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case POST: return handlePOST(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handlePOST(HttpRequest request) {
        String requestPath = request.getUri().getPath();
        if (requestPath.equals("/maintainer/v1")) {
            Inspector object = toSlime(request.getData()).get();
            if (object.type() != Type.ARRAY) {
                throw new IllegalArgumentException("Expected a list maintainer jobs to execute");
            }

            object.traverse((ArrayTraverser) (int i, Inspector item) -> {
                String type = getFieldOrFail(item, "type").asString();
                Inspector arguments = getFieldOrFail(item, "arguments");
                parseMaintenanceJob(type, arguments);
            });
            return new MessageResponse("Successfully executed " + object.entries() + " commands");

        } else if (requestPath.startsWith("/maintainer/v1/")) {
            String type = requestPath.substring(requestPath.lastIndexOf('/') + 1);
            Inspector arguments = toSlime(request.getData()).get();
            parseMaintenanceJob(type, arguments);
            return new MessageResponse("Successfully executed " + type);

        } else {
            return ErrorResponse.notFoundError("Nothing at path '" + requestPath + "'");
        }
    }

    public void parseMaintenanceJob(String type, Inspector arguments) {
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

    private void parseDeleteFilesJob(Inspector arguments) {
        Path basePath = Paths.get(getFieldOrFail(arguments, "basePath").asString());
        Duration maxAge = Duration.ofSeconds(getFieldOrFail(arguments, "maxAgeSeconds").asLong());
        Optional<String> fileNameRegex = SlimeUtils.optionalString(getFieldOrFail(arguments, "fileNameRegex"));
        boolean recursive = getFieldOrFail(arguments, "recursive").asBool();
        try {
            DeleteOldAppData.deleteFiles(basePath, maxAge, fileNameRegex, recursive);
        } catch (IOException e) {
            throw new RuntimeException("Failed deleting files under " + basePath.toAbsolutePath() +
                    fileNameRegex.map(regex -> ", matching '" + regex + "'").orElse("") +
                    ", " + (recursive ? "" : "not ") + "recursively" +
                    " and older than " + maxAge, e);
        }
    }

    private void parseDeleteDirectoriesJob(Inspector arguments) {
        Path basePath = Paths.get(getFieldOrFail(arguments, "basePath").asString());
        Duration maxAge = Duration.ofSeconds(getFieldOrFail(arguments, "maxAgeSeconds").asLong());
        Optional<String> dirNameRegex = SlimeUtils.optionalString(getFieldOrFail(arguments, "dirNameRegex"));
        try {
            DeleteOldAppData.deleteDirectories(basePath, maxAge, dirNameRegex);
        } catch (IOException e) {
            throw new RuntimeException("Failed deleting directories under " + basePath.toAbsolutePath() +
                    dirNameRegex.map(regex -> ", matching '" + regex + "'").orElse("") +
                    " and older than " + maxAge, e);
        }
    }

    private void parseRecursiveDelete(Inspector arguments) {
        Path basePath = Paths.get(getFieldOrFail(arguments, "path").asString());
        try {
            DeleteOldAppData.recursiveDelete(basePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed deleting " + basePath.toAbsolutePath(), e);
        }
    }

    private void parseMoveFiles(Inspector arguments) {
        Path from = Paths.get(getFieldOrFail(arguments, "from").asString());
        Path to = Paths.get(getFieldOrFail(arguments, "to").asString());

        try {
            DeleteOldAppData.moveIfExists(from, to);
        } catch (IOException e) {
            throw new RuntimeException("Failed moving from " + from.toAbsolutePath() + ", to " + to.toAbsolutePath(), e);
        }
    }

    private void parseHandleCoreDumps(Inspector arguments) {
        Path coredumpsPath = Paths.get(getFieldOrFail(arguments, "coredumpsPath").asString());
        Path doneCoredumpsPath = Paths.get(getFieldOrFail(arguments, "doneCoredumpsPath").asString());
        Map<String, Object> attributesMap = parseMap(getFieldOrFail(arguments, "attributes"));

        try {
            coredumpHandler.removeJavaCoredumps(coredumpsPath);
            coredumpHandler.processAndReportCoredumps(coredumpsPath, doneCoredumpsPath, attributesMap);
        } catch (IOException e) {
            throw new RuntimeException("Failed processing coredumps at " + coredumpsPath.toAbsolutePath() +
                    ", moving fished dumps to " + doneCoredumpsPath.toAbsolutePath(), e);
        }
    }

    private Map<String, Object> parseMap(Inspector object) {
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

    private Slime toSlime(InputStream jsonStream) {
        try {
            byte[] jsonBytes = IOUtils.readBytes(jsonStream, 1000 * 1000);
            return SlimeUtils.jsonToSlime(jsonBytes);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    public Inspector getFieldOrFail(Inspector object, String key) {
        Inspector out = object.field(key);
        if (out.type() == Type.NIX) {
            throw new IllegalArgumentException("Key '" + key + "' was not found!");
        }
        return out;
    }
}
