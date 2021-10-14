// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * The response binding for a complete cluster.
 *
 * @author Steinar Knutsen
 */
public class ClusterView {
    public String name;
    public String type;
    @JsonInclude(value = Include.NON_NULL)
    public String url;
    public List<ServiceView> services;
}
