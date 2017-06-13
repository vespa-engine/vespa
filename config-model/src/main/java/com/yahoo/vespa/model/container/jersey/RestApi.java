// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.config.jersey.JerseyInitConfig;
import com.yahoo.vespa.model.container.component.Component;

import java.util.Optional;

/**
 * @author gjoranv
 * @since 5.6
 */
public class RestApi extends AbstractConfigProducer<AbstractConfigProducer<?>> implements
        JerseyInitConfig.Producer
{
    public final boolean isJersey2;
    private final String bindingPath;
    private final Component<?, ?> jerseyHandler;
    private RestApiContext restApiContext;

    public RestApi(String bindingPath, boolean isJersey2) {
        super(idFromPath(bindingPath));
        this.bindingPath = bindingPath;
        this.isJersey2 = isJersey2;

        jerseyHandler = isJersey2 ?
                createJersey2Servlet(this.bindingPath):
                createJersey1Handler(this.bindingPath);
        addChild(jerseyHandler);
    }

    public static String idFromPath(String path) {
        return path.replaceAll("/", "|");
    }

    private Jersey2Servlet createJersey2Servlet(String bindingPath) {
        return new Jersey2Servlet(bindingPath);
    }

    private static JerseyHandler createJersey1Handler(String bindingPath) {
        JerseyHandler jerseyHandler = new JerseyHandler(bindingPath);
        jerseyHandler.addServerBindings(getBindings(bindingPath));
        return jerseyHandler;
    }

    public String getBindingPath() {
        return bindingPath;
    }

    @Override
    public void getConfig(JerseyInitConfig.Builder builder) {
        builder.jerseyMapping(bindingPath);
    }

    public void setRestApiContext(RestApiContext restApiContext) {
        this.restApiContext = restApiContext;
        addChild(restApiContext);
        jerseyHandler.inject(restApiContext);
    }

    public RestApiContext getContext() { return restApiContext; }

    public Optional<JerseyHandler> getJersey1Handler() {
        return isJersey2 ?
                Optional.empty():
                Optional.of((JerseyHandler)jerseyHandler);
    }

    public Optional<Jersey2Servlet> getJersey2Servlet() {
        return isJersey2 ?
                Optional.of((Jersey2Servlet)jerseyHandler) :
                Optional.empty();
    }

    private static String[] getBindings(String bindingPath) {
        String bindingWithoutScheme = "://*/" + bindingPath + "/*";
        return new String[] {"http" + bindingWithoutScheme, "https" + bindingWithoutScheme};
    }

    public void prepare() {
        restApiContext.prepare();
    }
}
