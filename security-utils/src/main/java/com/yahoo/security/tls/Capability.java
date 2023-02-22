// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.tls;

import java.util.Arrays;
import java.util.Optional;

/**
 * @author bjorncs
 */
public enum Capability implements ToCapabilitySet {
    NONE("vespa.none"), // placeholder for no capabilities
    HTTP_UNCLASSIFIED("vespa.http.unclassified"),
    RESTAPI_UNCLASSIFIED("vespa.restapi.unclassified"),
    RPC_UNCLASSIFIED("vespa.rpc.unclassified"),
    CLIENT__FILERECEIVER_API("vespa.client.filereceiver_api"),
    CLIENT__SLOBROK_API("vespa.client.slobrok_api"),
    CLUSTER_CONTROLLER__REINDEXING("vespa.cluster_controller.reindexing"),
    CLUSTER_CONTROLLER__STATE("vespa.cluster_controller.state"),
    CLUSTER_CONTROLLER__STATUS("vespa.cluster_controller.status"),
    CONFIGPROXY__CONFIG_API("vespa.configproxy.config_api"),
    CONFIGPROXY__MANAGEMENT_API("vespa.configproxy.management_api"),
    CONFIGPROXY__FILEDISTRIBUTION_API("vespa.configproxy.filedistribution_api"),
    CONFIGSERVER__CONFIG_API("vespa.configserver.config_api"),
    CONFIGSERVER__FILEDISTRIBUTION_API("vespa.configserver.filedistribution_api"),
    CONTAINER__DOCUMENT_API("vespa.container.document_api"),
    CONTAINER__MANAGEMENT_API("vespa.container.management_api"),
    CONTAINER__STATE_API("vespa.container.state_api"),
    CONTENT__CLUSTER_CONTROLLER__INTERNAL_STATE_API("vespa.content.cluster_controller.internal_state_api"),
    CONTENT__DOCUMENT_API("vespa.content.document_api"),
    CONTENT__METRICS_API("vespa.content.metrics_api"),
    CONTENT__PROTON_ADMIN_API("vespa.content.proton_admin_api"),
    CONTENT__SEARCH_API("vespa.content.search_api"),
    CONTENT__STATE_API("vespa.content.state_api"),
    CONTENT__STATUS_PAGES("vespa.content.status_pages"),
    CONTENT__STORAGE_API("vespa.content.storage_api"),
    LOGSERVER_API("vespa.logserver.api"),
    METRICSPROXY__MANAGEMENT_API("vespa.metricsproxy.management_api"),
    METRICSPROXY__METRICS_API("vespa.metricsproxy.metrics_api"),
    SENTINEL__CONNECTIVITY_CHECK("vespa.sentinel.connectivity_check"),
    SENTINEL__INSPECT_SERVICES("vespa.sentinel.inspect_services"),
    SENTINEL__MANAGEMENT_API("vespa.sentinel.management_api"),
    SLOBROK__API("vespa.slobrok.api"),
    ;

    private final String name;

    Capability(String name) { this.name = name; }

    public String asString() { return name; }

    @Override public CapabilitySet toCapabilitySet() { return CapabilitySet.of(this); }

    public static Optional<Capability> fromName(String n) { return Arrays.stream(values()).filter(c -> c.name.equals(n)).findAny(); }

}
