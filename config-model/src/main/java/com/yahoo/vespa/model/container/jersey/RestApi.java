// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey;

import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * Represents a rest-api
 *
 * @author gjoranv
 */
public class RestApi extends AbstractConfigProducer<AbstractConfigProducer<?>> {

    private final String bindingPath;
    private final Jersey2Servlet jerseyServlet;
    private RestApiContext restApiContext;

    public RestApi(String bindingPath) {
        super(idFromPath(bindingPath));
        this.bindingPath = bindingPath;

        jerseyServlet = createJersey2Servlet(this.bindingPath);
        addChild(jerseyServlet);
    }

    public static String idFromPath(String path) {
        return path.replaceAll("/", "|");
    }

    private Jersey2Servlet createJersey2Servlet(String bindingPath) {
        return new Jersey2Servlet(bindingPath);
    }

    public String getBindingPath() {
        return bindingPath;
    }

    public void setRestApiContext(RestApiContext restApiContext) {
        this.restApiContext = restApiContext;
        addChild(restApiContext);
        jerseyServlet.inject(restApiContext);
    }

    public RestApiContext getContext() { return restApiContext; }

    public Jersey2Servlet getJersey2Servlet() {
        return jerseyServlet;
    }

    public void prepare() {
        restApiContext.prepare();
    }
}
