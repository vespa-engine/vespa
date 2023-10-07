// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.pricing;

import com.yahoo.collections.Pair;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Plan;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PriceInformation;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingController;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.restapi.ErrorResponse.methodNotAllowed;
import static com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo.SupportLevel;
import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * API for calculating price information
 *
 * @author hmusum
 */
@SuppressWarnings("unused") // Handler
public class PricingApiHandler extends ThreadedHttpRequestHandler {

    private final static Logger log = Logger.getLogger(PricingApiHandler.class.getName());
    private static final BigDecimal SCALED_ZERO = new BigDecimal("0.00");

    private final Controller controller;

    @Inject
    public PricingApiHandler(Context parentCtx, Controller controller) {
        super(parentCtx);
        this.controller = controller;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (request.getMethod() != GET)
            return methodNotAllowed("Method '" + request.getMethod() + "' is not supported");

        try {
            return handleGET(request);
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/pricing/v1/pricing")) return pricing(request);

        return ErrorResponse.notFoundError(Text.format("No '%s' handler at '%s'", request.getMethod(),
                                                       request.getUri().getPath()));
    }

    private HttpResponse pricing(HttpRequest request) {
        String rawQuery = request.getUri().getRawQuery();
        PriceInformation price = parseQuery(rawQuery);
        return response(price);
    }

    private PriceInformation parseQuery(String rawQuery) {
        String[] elements = URLDecoder.decode(rawQuery, UTF_8).split("&");
        if (elements.length == 0) throw new IllegalArgumentException("no price information found in query");

        var supportLevel = SupportLevel.STANDARD;
        var enclave = false;
        var committedSpend = 0d;
        var plan = controller.serviceRegistry().planRegistry().defaultPlan();  // fallback to default plan if not supplied
        List<ClusterResources> clusterResources = new ArrayList<>();

        for (Pair<String, String> entry : keysAndValues(elements)) {
            switch (entry.getFirst()) {
                case "committedSpend" -> committedSpend = parseDouble(entry.getSecond());
                case "enclave" -> enclave = Boolean.parseBoolean(entry.getSecond());
                case "planId" -> plan = plan(entry.getSecond())
                        .orElseThrow(() -> new IllegalArgumentException("Unknown plan id " + entry.getSecond()));
                case "supportLevel" -> supportLevel = SupportLevel.valueOf(entry.getSecond().toUpperCase());
                case "resources" -> clusterResources.add(clusterResources(entry.getSecond()));
            }
        }
        if (clusterResources.size() < 1) throw new IllegalArgumentException("No cluster resources found in query");

        PricingInfo pricingInfo = new PricingInfo(enclave, supportLevel, committedSpend);
        return controller.serviceRegistry().pricingController().price(clusterResources, pricingInfo, plan);
    }

    private ClusterResources clusterResources(String resourcesString) {
        String[] elements = resourcesString.split(",");
        if (elements.length == 0)
            throw new IllegalArgumentException("nothing found in cluster resources: " + resourcesString);

        var nodes = 0;
        var vcpu = 0d;
        var memoryGb = 0d;
        var diskGb = 0d;
        var gpuMemoryGb = 0d;

        for (var element : keysAndValues(elements)) {
            switch (element.getFirst()) {
                case "nodes" -> nodes = parseInt(element.getSecond());
                case "vcpu" -> vcpu = parseDouble(element.getSecond());
                case "memoryGb" -> memoryGb = parseDouble(element.getSecond());
                case "diskGb" -> diskGb = parseDouble(element.getSecond());
                case "gpuMemoryGb" -> gpuMemoryGb = parseDouble(element.getSecond());
            }
        }

        var nodeResources = new NodeResources(vcpu, memoryGb, diskGb, 0); // 0 bandwidth, not used in price calculation
        if (gpuMemoryGb > 0)
            nodeResources = nodeResources.with(new NodeResources.GpuResources(1, gpuMemoryGb));
        return new ClusterResources(nodes, 1, nodeResources);
    }

    private static String getKeyAndValue(String element, int index) {
        return element.substring(0, index);
    }

    private List<Pair<String, String>> keysAndValues(String[] elements) {
        return Arrays.stream(elements).map(element -> {
                    var index = element.indexOf("=");
                    return new Pair<>(element.substring(0, index), element.substring(index + 1));
                })
                .collect(Collectors.toList());
    }

    private Optional<Plan> plan(String element) {
        return controller.serviceRegistry().planRegistry().plan(element);
    }

    private static SlimeJsonResponse response(PriceInformation priceInfo) {
        var slime = new Slime();
        Cursor cursor = slime.setObject();
        cursor.setString("listPrice", SCALED_ZERO.add(priceInfo.listPrice()).toPlainString());
        cursor.setString("volumeDiscount", SCALED_ZERO.add(priceInfo.volumeDiscount()).toPlainString());
        return new SlimeJsonResponse(slime);
    }

}
