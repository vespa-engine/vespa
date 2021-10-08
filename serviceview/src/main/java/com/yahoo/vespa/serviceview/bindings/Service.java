// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * View of {@link com.yahoo.cloud.config.ModelConfig.Hosts.Services}.
 *
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Service {
    public String name;
    public String type;
    public String configid;
    public String clustertype;
    public String clustername;
    public long index;
    public List<ServicePort> ports;

}
