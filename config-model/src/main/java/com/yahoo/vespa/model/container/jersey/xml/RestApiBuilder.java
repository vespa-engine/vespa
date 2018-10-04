// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.jersey.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.jersey.RestApi;
import com.yahoo.vespa.model.container.jersey.RestApiContext;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author gjoranv
 * @since 5.6
 */
public class RestApiBuilder extends VespaDomBuilder.DomConfigProducerBuilder<RestApi>  {

    @Override
    protected RestApi doBuild(DeployState deployState, AbstractConfigProducer ancestor, Element spec) {
        String bindingPath = spec.getAttribute("path");
        RestApi restApi = new RestApi(bindingPath);

        restApi.setRestApiContext(createRestApiContext(ancestor, spec, bindingPath));
        return restApi;
    }

    private RestApiContext createRestApiContext(AbstractConfigProducer ancestor, Element spec, String bindingPath) {
        RestApiContext restApiContext = new RestApiContext(ancestor, bindingPath);

        restApiContext.addBundles(getBundles(spec));

        return restApiContext;
    }

    private List<RestApiContext.BundleInfo> getBundles(Element spec) {
        List<RestApiContext.BundleInfo> bundles = new ArrayList<>();
        for (Element bundleElement : XML.getChildren(spec, "components")) {
            bundles.add(getBundle(bundleElement));
        }
        return bundles;
    }

    private RestApiContext.BundleInfo getBundle(Element bundleElement) {
        RestApiContext.BundleInfo bundle = new RestApiContext.BundleInfo(bundleElement.getAttribute("bundle"));

        for (Element packageElement : XML.getChildren(bundleElement, "package"))
            bundle.addPackageToScan(XML.getValue(packageElement));

        return bundle;
    }

    // TODO: use for naming injected components instead
    private Map<String, String> getInjections(Element spec) {
        Map<String, String> injectForClass = new LinkedHashMap<>();
        for (Element injectElement : XML.getChildren(spec, "inject")) {
            injectForClass.put(injectElement.getAttribute("for-class"),
                               injectElement.getAttribute("component"));
        }
        return injectForClass;
    }

}
