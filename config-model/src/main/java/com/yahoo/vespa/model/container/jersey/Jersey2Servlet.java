// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.VersionSpecification;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.Servlet;

/**
 * @author tonytv
 */
public class Jersey2Servlet extends Servlet {

    public static final String BUNDLE = "container-jersey2";
    public static final String CLASS = "com.yahoo.container.servlet.jersey.JerseyServletProvider";

    private static final ComponentId REST_API_NAMESPACE = ComponentId.fromString("rest-api");

    public Jersey2Servlet(String bindingPath) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(idSpecFromPath(bindingPath),
                                                     ComponentSpecification.fromString(CLASS),
                                                     ComponentSpecification.fromString(BUNDLE))),
              bindingPath + "/*");
    }

    private static ComponentSpecification idSpecFromPath(String path) {
        return new ComponentSpecification(RestApi.idFromPath(path),
                                          VersionSpecification.emptyVersionSpecification,
                                          REST_API_NAMESPACE);
    }

}
