// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.pricing;

import com.yahoo.collections.Pair;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ClusterResources;
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
import com.yahoo.vespa.hosted.controller.api.integration.pricing.ApplicationResources;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PriceInformation;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.Prices;
import com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.yolean.Exceptions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.restapi.ErrorResponse.methodNotAllowed;
import static com.yahoo.vespa.hosted.controller.api.integration.pricing.PricingInfo.SupportLevel;
import static java.math.BigDecimal.ZERO;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * API for calculating price information
 *
 * @author hmusum
 */
@SuppressWarnings("unused") // Handler
public class PricingApiHandler extends ThreadedHttpRequestHandler {

    private static final Logger log = Logger.getLogger(PricingApiHandler.class.getName());

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
        var priceParameters = parseQuery(rawQuery);
        Prices price = calculatePrice(priceParameters);
        return response(price, priceParameters);
    }

    private Prices calculatePrice(PriceParameters priceParameters) {
        var priceCalculator = controller.serviceRegistry().pricingController();
        return priceCalculator.priceForApplications(priceParameters.appResources, priceParameters.pricingInfo, priceParameters.plan);
    }

    private PriceParameters parseQuery(String rawQuery) {
        if (rawQuery == null) throw new IllegalArgumentException("No price information found in query");
        List<String> elements = Arrays.stream(URLDecoder.decode(rawQuery, UTF_8).split("&")).toList();
        return parseQuery(elements);
    }

    private PriceParameters parseQuery(List<String> elements) {
        var supportLevel = SupportLevel.BASIC;
        var enclave = false;
        var committedSpend = ZERO;
        var applicationName = "default";
        var plan = controller.serviceRegistry().planRegistry().defaultPlan(); // fallback to default plan if not supplied
        List<ApplicationResources> appResources = new ArrayList<>();

        for (Pair<String, String> entry : keysAndValues(elements)) {
            var value = entry.getSecond();
            switch (entry.getFirst().toLowerCase()) {
                case "committedspend" -> committedSpend = new BigDecimal(value);
                case "planid" -> plan = plan(value).orElseThrow(() -> new IllegalArgumentException("Unknown plan id " + value));
                case "supportlevel" -> supportLevel = SupportLevel.valueOf(value.toUpperCase());
                case "application" -> appResources.add(applicationResources(value));
                default -> throw new IllegalArgumentException("Unknown query parameter '" + entry.getFirst() + '\'');
            }
        }

        PricingInfo pricingInfo = new PricingInfo(supportLevel, committedSpend);
        return new PriceParameters(List.of(), pricingInfo, plan, appResources);
    }

    private ApplicationResources applicationResources(String appResourcesString) {
        List<String> elements = List.of(appResourcesString.split(","));

        var vcpu = ZERO;
        var memoryGb = ZERO;
        var diskGb = ZERO;
        var gpuMemoryGb = ZERO;
        var enclaveVcpu = ZERO;
        var enclaveMemoryGb = ZERO;
        var enclaveDiskGb = ZERO;
        var enclaveGpuMemoryGb = ZERO;

        for (var element : keysAndValues(elements)) {
            var value = element.getSecond();
            switch (element.getFirst().toLowerCase()) {
                case "vcpu" -> vcpu = new BigDecimal(value);
                case "memorygb" -> memoryGb = new BigDecimal(value);
                case "diskgb" -> diskGb = new BigDecimal(value);
                case "gpumemorygb" -> gpuMemoryGb = new BigDecimal(value);

                case "enclavevcpu" -> enclaveVcpu = new BigDecimal(value);
                case "enclavememorygb" -> enclaveMemoryGb = new BigDecimal(value);
                case "enclavediskgb" -> enclaveDiskGb = new BigDecimal(value);
                case "enclavegpumemorygb" -> enclaveGpuMemoryGb = new BigDecimal(value);

                default -> throw new IllegalArgumentException("Unknown key '" + element.getFirst() + '\'');
            }
        }

        return new ApplicationResources(vcpu, memoryGb, diskGb, gpuMemoryGb,
                                        enclaveVcpu, enclaveMemoryGb, enclaveDiskGb, enclaveGpuMemoryGb);
    }

    private List<Pair<String, String>> keysAndValues(List<String> elements) {
        return elements.stream().map(element -> {
                    var index = element.indexOf("=");
                    if (index <= 0 || index == element.length() - 1)
                        throw new IllegalArgumentException("Error in query parameter, expected '=' between key and value: '" + element + '\'');
                    return new Pair<>(element.substring(0, index), element.substring(index + 1));
                })
                .toList();
    }

    private Optional<Plan> plan(String element) {
        return controller.serviceRegistry().planRegistry().plan(element);
    }

    private static SlimeJsonResponse response(Prices prices, PriceParameters priceParameters) {
        var slime = new Slime();
        Cursor cursor = slime.setObject();

        var applicationsArray = cursor.setArray("applications");
        applicationPrices(applicationsArray, prices.priceInformationApplications(), priceParameters);

        var priceInfoArray = cursor.setArray("priceInfo");
        addItem(priceInfoArray, "Enclave (minimum $10k per month)", prices.totalPriceInformation().enclaveDiscount());
        addItem(priceInfoArray, "Committed spend", prices.totalPriceInformation().committedAmountDiscount());

        setBigDecimal(cursor, "totalAmount", prices.totalPriceInformation().totalAmount());

        return new SlimeJsonResponse(slime);
    }

    private static void applicationPrices(Cursor applicationPricesArray, List<PriceInformation> applicationPrices, PriceParameters priceParameters) {
        applicationPrices.forEach(priceInformation -> {
            var element = applicationPricesArray.addObject();
            var array = element.setArray("priceInfo");
            addItem(array, supportLevelDescription(priceParameters), priceInformation.listPriceWithSupport());
            addItem(array, "Enclave", priceInformation.enclaveDiscount());
            addItem(array, "Volume discount", priceInformation.volumeDiscount());
        });
    }

    private static String supportLevelDescription(PriceParameters priceParameters) {
        String supportLevel = priceParameters.pricingInfo.supportLevel().name();
        return supportLevel.substring(0,1).toUpperCase() + supportLevel.substring(1).toLowerCase() + " support unit price";
    }

    private static void addItem(Cursor array, String name, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) != 0) {
            var o = array.addObject();
            o.setString("description", name);
            setBigDecimal(o, "amount", amount);
        }
    }

    private static void setBigDecimal(Cursor cursor, String name, BigDecimal value) {
        cursor.setString(name, value.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private record PriceParameters(List<ClusterResources> clusterResources, PricingInfo pricingInfo, Plan plan,
                                   List<ApplicationResources> appResources) {

    }

}
