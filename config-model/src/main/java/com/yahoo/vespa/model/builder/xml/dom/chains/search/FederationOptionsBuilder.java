// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.search.searchchain.model.federation.FederationOptions;
import org.w3c.dom.Element;


/**
 * Builds federation options from a federations options element
 * @author Tony Vaagenes
 */
public class FederationOptionsBuilder {
    public static final String federationOptionsElement = "federationoptions";

    private final FederationOptions federationOptions;

    FederationOptionsBuilder(Element spec) {
        federationOptions =
                new FederationOptions().
                        setUseByDefault(readUseByDefault(spec)).
                        setOptional(readOptional(spec)).
                        setTimeoutInMilliseconds(readTimeout(spec)).
                        setRequestTimeoutInMilliseconds(readRequestTimeout(spec));
    }


    private Integer readTimeout(Element spec) {
        String timeout = spec.getAttribute("timeout");

        return (timeout.isEmpty())?
                null :
                TimeParser.asMilliSeconds(timeout);
    }

    private Integer readRequestTimeout(Element spec) {
        String requestTimeout = spec.getAttribute("requestTimeout");

        return (requestTimeout.isEmpty())?
                null :
                TimeParser.asMilliSeconds(requestTimeout);
    }

    private Boolean readOptional(Element spec) {
        String optional = spec.getAttribute("optional");
        return (optional.isEmpty()) ?
                null :
                Boolean.parseBoolean(optional);
    }

    private Boolean readUseByDefault(Element spec) {
        String useByDefault = spec.getAttribute("default");
        return (useByDefault.isEmpty()) ?
                null :
                Boolean.parseBoolean(useByDefault);
    }

    FederationOptions build() {
        return federationOptions;
    }
}
