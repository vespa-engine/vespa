package ai.vespa.metrics;
// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @author yngveaasheim
 */
public enum Labels {

    // Any changes to labels in the ai.vespa namespace needs to be approved in an architecture reviews.
    // We try to follow recommendations outlined in OpenTelemetry Semantic Conventions for these labels, https://opentelemetry.io/docs/specs/semconv/.

    // Labels to be decorated onto all tenant related metrics generated for Vespa Cloud.
    CLUSTER("ai.vespa.cluster", "The name of a Vespa cluster."),
    CLUSTER_TYPE("ai.vespa.cluster_type", "The type of a Vespa cluster, typically one of 'admin', 'container', 'content'."),
    DEPLOYMENT_CLUSTER("ai.vespa.deployment_cluster", "Unique ID for a Vespa deployment cluster, in the format <tenant>.<application>.<instance>.<zone>.<cluster>."),
    INSTANCE("ai.vespa.instance", "The id of a Vespa application instance in the format <tenant>.<application>.<instance>."),
    GROUP("ai.vespa.group", "The group id of a Vespa content node. Samples values are 'Group 1', 'Group 2', etc."),
    SYSTEM("ai.vespa.system", "The name of a managed Vespa system, sample values are 'public', 'publiccd'."),
    ZONE("ai.vespa.zone", "The name of a zone in managed Vespa, in the format <environment>.<region>. Sample name 'prod.aws-us-west-2a'."),
    PARENT("ai.vespa.parent", "The fully qualified name of the parent host on which a Vespa node is running."),
    NODE("ai.vespa.node", "The fully qualified name of the Vespa node."),

    // Labels used for a subset of the metrics only:
    CHAIN("ai.vespa.chain", "The name of a search chain"),
    DOCUMENT_PROCESSOR("ai.vespa.document_processor", "Document processor name."),
    SERVICE("ai.vespa.service", "Vespa service name, e.g. 'container', 'distributor', 'searchnode'."),
    THREAD_POOL("ai.vespa.thread_pool", "Thread pool name."),
    VERSION("ai.vespa.version", "Version of Vespa running on a node."),

    // TODO: Add other labels used by the metrics in the summary dashboard: "api, gcName, gpu, interface, operation, protocol(verify, requestType, role, scheme, status(verify)

    // Labels defined by OpenTelemetry Semantic Conventions external to Vespa
    HOST_ARCH("host.arch", "The CPU architecture of a host, e.g. 'x86_64', 'arm64'. See also https://opentelemetry.io/docs/specs/semconv/resource/host/"),
    HOST_NAME("host.name", "The fully qualified name of a host. See also https://opentelemetry.io/docs/specs/semconv/resource/host/"),
    HOST_TYPE("host.type", "The type of a host. See also https://opentelemetry.io/docs/specs/semconv/resource/host/"),
    HTTP_REQUEST_METHOD("http.request.method", "The HTTP request method specified. See also https://opentelemetry.io/docs/specs/semconv/attributes-registry/http/"),
    HTTP_RESPONSE_STATUS_CODE("http.response.status_code", "The HTTP response code. See also https://opentelemetry.io/docs/specs/semconv/attributes-registry/http/");

    private final String name;
    private final String description;

    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }

    Labels(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
