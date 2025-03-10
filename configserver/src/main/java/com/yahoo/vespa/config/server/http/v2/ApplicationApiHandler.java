// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.utils.MultiPartFormParser;
import com.yahoo.container.jdisc.utils.MultiPartFormParser.PartItem;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.http.v2.response.SessionPrepareAndActivateResponse;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.yolean.Exceptions;
import org.apache.hc.core5.http.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yahoo.vespa.config.server.application.CompressedApplicationInputStream.createFromCompressedStream;
import static com.yahoo.vespa.config.server.http.Utils.checkThatTenantExists;
import static com.yahoo.vespa.config.server.http.v2.SessionCreateHandler.validateDataAndHeader;
import static com.yahoo.vespa.flags.PermanentFlags.VERBOSE_DEPLOY_PARAMETER;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

/**
 *  * The implementation of the /application/v2 API.
 *
 *
 * @author hmusum
 */
// TODO: only handles 'prepareandactive' for now, merge other handlers into this one
public class ApplicationApiHandler extends SessionHandler {

    public final static String APPLICATION_X_GZIP = "application/x-gzip";
    public final static String APPLICATION_ZIP = "application/zip";
    public final static String MULTIPART_FORM_DATA = "multipart/form-data";
    public final static String MULTIPART_PARAMS = "prepareParams";
    public final static String MULTIPART_APPLICATION_PACKAGE = "applicationPackage";
    public final static String contentTypeHeader = "Content-Type";

    private final TenantRepository tenantRepository;
    private final Duration zookeeperBarrierTimeout;
    private final long maxApplicationPackageSize;
    private final Zone zone;

    @Inject
    public ApplicationApiHandler(Context ctx,
                                 ApplicationRepository applicationRepository,
                                 ConfigserverConfig configserverConfig,
                                 Zone zone) {
        super(ctx, applicationRepository);
        this.tenantRepository = applicationRepository.tenantRepository();
        this.zookeeperBarrierTimeout = Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout());
        this.maxApplicationPackageSize = configserverConfig.maxApplicationPackageSize();
        this.zone = zone;
    }

    @Override
    protected HttpResponse handlePUT(HttpRequest request) {
        TenantName tenantName = validateTenant(request);
        long sessionId = getSessionIdFromRequest(request);
        ApplicationId app = applicationRepository.activate(tenantRepository.getTenant(tenantName),
                                                           sessionId,
                                                           getTimeoutBudget(request, Duration.ofMinutes(2)),
                                                           shouldIgnoreSessionStaleFailure(request));
        return new MessageResponse("Session " + sessionId + " for " + app.toFullString() + " activated");
    }

    @Override
    protected HttpResponse handlePOST(HttpRequest request) {
        validateDataAndHeader(request, List.of(APPLICATION_X_GZIP, APPLICATION_ZIP, MULTIPART_FORM_DATA));
        TenantName tenantName = validateTenant(request);

        PrepareParams prepareParams;
        CompressedApplicationInputStream compressedStream;
        boolean multipartRequest = Optional.ofNullable(request.getHeader(HttpHeaders.Names.CONTENT_TYPE))
                .map(ContentType::parse)
                .map(contentType -> contentType.getMimeType().equalsIgnoreCase(MULTIPART_FORM_DATA))
                .orElse(false);
        if (multipartRequest) {
            Map<String, PartItem> parts = Map.of();
            try {
                parts = new MultiPartFormParser(request).readParts();
                byte[] params;
                try (InputStream part = parts.get(MULTIPART_PARAMS).data()) { params = part.readAllBytes(); }
                log.log(FINE, "Deploy parameters: [{0}]", new String(params, StandardCharsets.UTF_8));
                prepareParams = PrepareParams.fromJson(params, tenantName, zookeeperBarrierTimeout,
                                                       VERBOSE_DEPLOY_PARAMETER.bindTo(applicationRepository.flagSource()).value());
                PartItem appPackagePart = parts.get(MULTIPART_APPLICATION_PACKAGE);
                compressedStream = createFromCompressedStream(appPackagePart.data(), appPackagePart.contentType(), maxApplicationPackageSize);
            } catch (IOException e) {
                var message = "Deploy request from '" + tenantName.value() + "' contains invalid data: " + e.getMessage();
                if (e instanceof MultiPartFormParser.MultiPartException)
                    log.log(INFO, "Unable to parse multipart in deploy from tenant '" + tenantName.value() + "': " +
                            Exceptions.toMessageString(e) + ". This is usually caused by controller abandoning request " +
                            "while streaming data to config server");
                else
                    log.log(INFO, message + ", parts: " + parts, e);
                throw new BadRequestException(message);
            }
        } else {
            prepareParams = PrepareParams.fromHttpRequest(request, tenantName, zookeeperBarrierTimeout);
            compressedStream = createFromCompressedStream(request.getData(), request.getHeader(contentTypeHeader), maxApplicationPackageSize);
        }

        // Aid debugging by adding full application id to access log (since only tenant name is part of the request URI path)
        request.getAccessLogEntry()
                .ifPresent(e -> e.addKeyValue("app.id", prepareParams.getApplicationId().toFullString()));

        try (compressedStream) {
            PrepareAndActivateResult result = applicationRepository.deploy(compressedStream, prepareParams);
            return new SessionPrepareAndActivateResponse(result, prepareParams.getApplicationId(), request, zone);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Duration getTimeout() {
        return zookeeperBarrierTimeout.plus(Duration.ofSeconds(180));
    }

    private TenantName validateTenant(HttpRequest request) {
        TenantName tenantName = getTenantNameFromRequest(request);
        checkThatTenantExists(tenantRepository, tenantName);
        return tenantName;
    }

    public static TenantName getTenantNameFromRequest(HttpRequest request) {
        BindingMatch<?> bm = Utils.getBindingMatch(request, "http://*/application/v2/tenant/*/prepareandactivate*"); // Gosh, these glob rules aren't good ...
        return TenantName.from(bm.group(2));
    }

    public static long getSessionIdFromRequest(HttpRequest request) {
        BindingMatch<?> bm = Utils.getBindingMatch(request, "http://*/application/v2/tenant/*/prepareandactivate/*");
        try {
            return Long.parseLong(bm.group(3));
        }
        catch (NumberFormatException e) {
            throw new BadRequestException("Session id '" + bm.group(3) + "' is not a number: " + e.getMessage());
        }
    }

}
