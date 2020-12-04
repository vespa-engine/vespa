// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing.http;

import ai.vespa.reindexing.Reindexing;
import ai.vespa.reindexing.ReindexingCurator;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.jdisc.Metric;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.vespa.config.content.reindexing.ReindexingConfig;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;

import java.util.concurrent.Executor;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;

/**
 * Allows inspecting reindexing status over HTTP.
 *
 * @author jonmv
 */
public class ReindexingV1ApiHandler extends ThreadedHttpRequestHandler {

    private final ReindexingCurator database;

    @Inject
    public ReindexingV1ApiHandler(Executor executor, Metric metric,
                                  @SuppressWarnings("unused") VespaZooKeeperServer ensureZkHasStarted, ZookeepersConfig zookeepersConfig,
                                  ReindexingConfig reindexingConfig, DocumentmanagerConfig documentmanagerConfig) {
        this(executor,
             metric,
             new ReindexingCurator(Curator.create(zookeepersConfig.zookeeperserverlist()),
                                   reindexingConfig.clusterName(),
                                   new DocumentTypeManager(documentmanagerConfig)));
    }

    ReindexingV1ApiHandler(Executor executor, Metric metric, ReindexingCurator database) {
        super(executor, metric);
        this.database = database;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (request.getMethod() != GET)
            return ErrorResponse.methodNotAllowed("Only GET is supported under /reindexing/v1/");

        if (path.matches("/reindexing/v1"))        return getRoot();
        if (path.matches("/reindexing/v1/status")) return getStatus();

        return ErrorResponse.notFoundError("Nothing at " + request.getUri().getRawPath());
    }

    HttpResponse getRoot() {
        Slime slime = new Slime();
        slime.setObject().setArray("resources").addObject().setString("url", "/reindexing/v1/status");
        return new SlimeJsonResponse(slime);
    }

    HttpResponse getStatus() {
        Slime slime = new Slime();
        Cursor statusArray = slime.setObject().setArray("status");
        database.readReindexing().status().forEach((type, status) -> {
            Cursor statusObject = statusArray.addObject();
            statusObject.setString("type", type.getName());
            statusObject.setLong("startedMillis", status.startedAt().toEpochMilli());
            status.endedAt().ifPresent(endedAt -> statusObject.setLong("endedMillis", endedAt.toEpochMilli()));
            status.progress().ifPresent(progress -> statusObject.setString("progress", progress.serializeToString()));
            statusObject.setString("state", toString(status.state()));
            status.message().ifPresent(message -> statusObject.setString("message", message));
        });
        return new SlimeJsonResponse(slime);
    }


    private static String toString(Reindexing.State state) {
        switch (state) {
            case READY: return "pending";
            case RUNNING: return "running";
            case SUCCESSFUL: return "successful";
            case FAILED: return "failed";
            default: throw new IllegalArgumentException("Unexpected state '" + state + "'");
        }
    }

}
