// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The response wrapper for the link to a single service state API.
 *
 * @author Steinar Knutsen
 */
public class ServiceView {
    public String url;
    public String serviceType;
    public String serviceName;
    public String configId;
    public String host;
    @JsonInclude(value = Include.NON_NULL)
    public String legacyStatusPages;
}
