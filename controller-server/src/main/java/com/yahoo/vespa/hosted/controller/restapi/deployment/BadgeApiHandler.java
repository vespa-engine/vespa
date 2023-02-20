// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.JobStatus;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This API serves redirects to a badge server.
 * 
 * @author jonmv
 */
@SuppressWarnings("unused") // Handler
public class BadgeApiHandler extends ThreadedHttpRequestHandler {

    private final static Logger log = Logger.getLogger(BadgeApiHandler.class.getName());

    private final Controller controller;
    private final Map<Key, Value> badgeCache = new ConcurrentHashMap<>();

    public BadgeApiHandler(Context parentCtx, Controller controller) {
        super(parentCtx);
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        Method method = request.getMethod();
        try {
            return switch (method) {
                case GET -> get(request);
                default -> ErrorResponse.methodNotAllowed("Method '" + method + "' is unsupported");
            };
        } catch (IllegalArgumentException|IllegalStateException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse get(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/badge/v1/{tenant}/{application}/{instance}")) return overviewBadge(path.get("tenant"), path.get("application"), path.get("instance"));
        if (path.matches("/badge/v1/{tenant}/{application}/{instance}/{jobName}")) return historyBadge(path.get("tenant"), path.get("application"), path.get("instance"), path.get("jobName"), request.getProperty("historyLength"));

        return ErrorResponse.notFoundError(Text.format("No '%s' handler at '%s'", request.getMethod(),
                                                         request.getUri().getPath()));
    }

    /** Returns a URI which points to an overview badge for the given application. */
    private HttpResponse overviewBadge(String tenant, String application, String instance) {
        ApplicationId id = ApplicationId.from(tenant, application, instance);
        return cachedResponse(new Key(id, null, 0),
                              controller.clock().instant(),
                              () -> {
                                  DeploymentStatus status = controller.jobController().deploymentStatus(controller.applications().requireApplication(TenantAndApplicationId.from(id)));
                                  Predicate<JobStatus> isDeclaredJob = job -> status.jobSteps().get(job.id()) != null && status.jobSteps().get(job.id()).isDeclared();
                                  return Badges.overviewBadge(id, status.jobs().instance(id.instance()).matching(isDeclaredJob));
                              });
    }

    /** Returns a URI which points to a history badge for the given application and job type. */
    private HttpResponse historyBadge(String tenant, String application, String instance, String jobName, String historyLength) {
        ApplicationId id = ApplicationId.from(tenant, application, instance);
        JobType type = JobType.fromJobName(jobName, controller.zoneRegistry());
        int length = historyLength == null ? 5 : Math.min(32, Math.max(0, Integer.parseInt(historyLength)));
        return cachedResponse(new Key(id, type, length),
                              controller.clock().instant(),
                              () -> Badges.historyBadge(id,
                                                        controller.jobController().jobStatus(new JobId(id, type)),
                                                        length)
        );
    }

    private HttpResponse cachedResponse(Key key, Instant now, Supplier<String> badge)  {
        return svgResponse(badgeCache.compute(key, (__, value) -> {
            return value != null && value.expiry.isAfter(now) ? value : new Value(badge.get(), now);
        }).badgeSvg);
    }

    private static HttpResponse svgResponse(String svg) {
        return new HttpResponse(200) {
            @Override public void render(OutputStream outputStream) throws IOException {
                outputStream.write(svg.getBytes(UTF_8));
            }
            @Override public String getContentType() {
                return "image/svg+xml; charset=UTF-8";
            }
        };
    }


    private static class Key {

        private final ApplicationId id;
        private final JobType type;
        private final int historyLength;

        private Key(ApplicationId id, JobType type, int historyLength) {
            this.id = id;
            this.type = type;
            this.historyLength = historyLength;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return historyLength == key.historyLength && id.equals(key.id) && Objects.equals(type, key.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, type, historyLength);
        }

    }

    private static class Value {

        private final String badgeSvg;
        private final Instant expiry;

        private Value(String badgeSvg, Instant created) {
            this.badgeSvg = badgeSvg;
            this.expiry = created.plusSeconds(60);
        }

    }

}
