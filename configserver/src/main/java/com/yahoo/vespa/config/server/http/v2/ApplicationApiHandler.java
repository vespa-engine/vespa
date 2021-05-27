// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.http.BadRequestException;
import com.yahoo.vespa.config.server.http.SessionHandler;
import com.yahoo.vespa.config.server.http.Utils;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.model.content.Content;
import org.apache.hc.core5.http.ContentType;
import org.eclipse.jetty.http.MultiPartFormInputStream;

import javax.servlet.http.Part;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.vespa.config.server.application.CompressedApplicationInputStream.createFromCompressedStream;
import static com.yahoo.vespa.config.server.http.Utils.checkThatTenantExists;
import static com.yahoo.vespa.config.server.http.v2.SessionCreateHandler.validateDataAndHeader;

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
    private final Zone zone;

    @Inject
    public ApplicationApiHandler(Context ctx,
                                 ApplicationRepository applicationRepository,
                                 ConfigserverConfig configserverConfig,
                                 Zone zone) {
        super(ctx, applicationRepository);
        this.tenantRepository = applicationRepository.tenantRepository();
        this.zookeeperBarrierTimeout = Duration.ofSeconds(configserverConfig.zookeeper().barrierTimeout());
        this.zone = zone;
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
        if(multipartRequest) {
            try {
                MultiPartFormInputStream multiPartFormInputStream = new MultiPartFormInputStream(request.getData(), request.getHeader(CONTENT_TYPE), /* config */null, /* contextTmpDir */null);
                Map<String, Part> parts = multiPartFormInputStream.getParts().stream()
                        .collect(Collectors.toMap(Part::getName, p -> p));

                byte[] params = parts.get(MULTIPART_PARAMS).getInputStream().readAllBytes();
                log.log(Level.FINE, "Deploy parameters: [{}]", new String(params, StandardCharsets.UTF_8));
                prepareParams = PrepareParams.fromJson(params, tenantName, zookeeperBarrierTimeout);
                Part appPackagePart = parts.get(MULTIPART_APPLICATION_PACKAGE);
                compressedStream = createFromCompressedStream(appPackagePart.getInputStream(), appPackagePart.getContentType());
            } catch (IOException e) {
                log.log(Level.WARNING, "Unable to parse multipart in deploy", e);
                throw new BadRequestException("Request contains invalid data");
            }
        } else {
            prepareParams = PrepareParams.fromHttpRequest(request, tenantName, zookeeperBarrierTimeout);
            compressedStream = createFromCompressedStream(request.getData(), request.getHeader(contentTypeHeader));
        }

        PrepareResult result = applicationRepository.deploy(compressedStream, prepareParams);
        return new SessionPrepareAndActivateResponse(result, request, prepareParams.getApplicationId(), zone);
    }

    @Override
    public Duration getTimeout() {
        return zookeeperBarrierTimeout.plus(Duration.ofSeconds(10));
    }

    private TenantName validateTenant(HttpRequest request) {
        TenantName tenantName = getTenantNameFromRequest(request);
        checkThatTenantExists(tenantRepository, tenantName);
        return tenantName;
    }

    public static TenantName getTenantNameFromRequest(HttpRequest request) {
        BindingMatch<?> bm = Utils.getBindingMatch(request, "http://*/application/v2/tenant/*/prepareandactivate*");
        return TenantName.from(bm.group(2));
    }

}
