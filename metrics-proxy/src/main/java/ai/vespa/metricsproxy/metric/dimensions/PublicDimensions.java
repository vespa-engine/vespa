// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.dimensions;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The names of all dimensions that are publicly available, in addition to some dimensions that
 * are used in the process of composing these public dimensions.
 *
 * 'INTERNAL' in this context means non-public.
 *
 * @author gjoranv
 */
public final class PublicDimensions {
    private PublicDimensions() { }

    public static final String APPLICATION_ID = "applicationId";  // <tenant.app.instance>
    public static final String ZONE = "zone";

    // The public CLUSTER_ID dimension value is composed from the two non-public dimensions.
    // Node-specific.
    public static final String INTERNAL_CLUSTER_TYPE = "clustertype";
    public static final String INTERNAL_CLUSTER_ID = "clusterid";
    public static final String CLUSTER_ID = "clusterId";
    public static final String DEPLOYMENT_CLUSTER = "deploymentCluster";

    // This dimension is not currently (March 2021) added to the 'commonDimensions' allow-list below, due to the
    // limit of 10 total dimensions in public http apis. See e.g. MetricsV2Handler#MAX_DIMENSIONS.
    public static final String GROUP_ID = "groupId";

    // Internal name (instance) is confusing, so renamed to 'serviceId' for public use.
    // This is added by the metrics-proxy.
    public static final String INTERNAL_SERVICE_ID = "instance";
    public static final String SERVICE_ID = "serviceId";

    // From host-admin, currently (Jan 2020) only included for 'vespa.node' metrics
    public static final String HOSTNAME = "host";


    /**  Metric specific dimensions  **/
    public static final String API = "api";                      // feed
    public static final String CHAIN = "chain";                  // query
    public static final String DOCUMENT_TYPE = "documenttype";   // content
    public static final String ENDPOINT = "endpoint";            // query
    public static final String GC_NAME = "gcName";               // container
    public static final String HTTP_METHOD = "httpMethod";       // container
    public static final String OPERATION = "operation";          // feed
    public static final String RANK_PROFILE = "rankProfile";     // content
    public static final String REASON = "reason";                // query (degraded etc.)
    public static final String STATUS = "status";                // feed


    // Dimensions that are valid (but not necessarily used) for all metrics.
    public static List<String> commonDimensions =
            List.of(APPLICATION_ID,
                    CLUSTER_ID,
                    HOSTNAME,
                    SERVICE_ID,
                    ZONE);

    // Dimensions that are only used for a subset of metrics.
    public static List<String> metricDimensions =
        List.of(API,
                CHAIN,
                DOCUMENT_TYPE,
                ENDPOINT,
                GC_NAME,
                HTTP_METHOD,
                OPERATION,
                RANK_PROFILE,
                REASON,
                STATUS);


    /**
     * All public dimensions, common dimensions first, then dimensions for individual metrics
     */
    public static final List<String> publicDimensions = Stream.concat(commonDimensions.stream(), metricDimensions.stream())
            .collect(Collectors.toList());

}
