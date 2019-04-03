package com.yahoo.vespa.hosted.controller.restapi.cost;

import com.yahoo.config.provision.CloudName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.restapi.Path;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponse;
import com.yahoo.vespa.hosted.controller.restapi.StringResponse;
import com.yahoo.vespa.hosted.controller.restapi.cost.config.SelfHostedCostConfig;

import java.time.Clock;
import java.util.Optional;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;

public class CostApiHandler extends LoggingRequestHandler {

    private final Controller controller;
    private final NodeRepositoryClientInterface nodeRepository;
    private final SelfHostedCostConfig selfHostedCostConfig;

    public CostApiHandler(Context ctx, Controller controller, NodeRepositoryClientInterface nodeRepository, SelfHostedCostConfig selfHostedCostConfig) {
        super(ctx);
        this.controller = controller;
        this.nodeRepository = nodeRepository;
        this.selfHostedCostConfig = selfHostedCostConfig;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        if (request.getMethod() != GET) {
            return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
        }

        Path path = new Path(request.getUri());

        if (path.matches("/cost/v1/csv")) {
            Optional<String> cloudProperty = Optional.ofNullable(request.getProperty("cloud"));
            CloudName cloud = cloudProperty.map(CloudName::from).orElse(CloudName.defaultName());
            return new StringResponse(CostCalculator.resourceShareByPropertyToCsv(nodeRepository, controller, Clock.systemUTC(), selfHostedCostConfig, cloud));
        }

        return ErrorResponse.notFoundError("Nothing at " + path);
    }
}
