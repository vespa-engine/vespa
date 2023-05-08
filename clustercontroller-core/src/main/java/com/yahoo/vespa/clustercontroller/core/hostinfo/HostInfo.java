// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parsing and keeping of host info from nodes.
 *
 * @author Haakon Dybdahl
 */
public class HostInfo {

    private static final Logger log = Logger.getLogger(HostInfo.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    private String rawCreationString = "NOT SET";
    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // TODO: Don't use JSON classes as model classes
    @JsonProperty("cluster-state-version") private Integer clusterStateVersion = null;
    @JsonProperty("vtag") private Vtag vtag = new Vtag(null);
    @JsonProperty("distributor") private Distributor distributor = new Distributor();
    @JsonProperty("metrics") private Metrics metrics = new Metrics();
    @JsonProperty("content-node") private ContentNode contentNode = new ContentNode();

    public Vtag getVtag() {
        return vtag;
    }

    public Distributor getDistributor() {
        return distributor;
    }

    public ContentNode getContentNode() {
        return contentNode;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Integer getClusterStateVersionOrNull() { return clusterStateVersion; }

    public static HostInfo createHostInfo(String json) {
        HostInfo hostInfo;
        try {
           hostInfo = mapper.readValue(json, HostInfo.class);
       } catch (IOException e) {
           log.log(Level.WARNING, "Problem parsing " + json, e);
           hostInfo = new HostInfo();
       }
       hostInfo.setRawCreationString(json);
       return hostInfo;
    }

    /**
     * Only for debugging.
     * @return string that was used to create this instance.
     */
    public String getRawCreationString() {
        return rawCreationString;
    }

    public void setRawCreationString(String rawCreationString) {
        this.rawCreationString = rawCreationString;
    }

    public HostInfo() {}

}
