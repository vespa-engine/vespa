package ai.vespa.metricsproxy.metric.dimensions;

import java.util.ArrayList;
import java.util.List;

/**
 * @author olaa
 */
public class BlocklistDimensions {

    private BlocklistDimensions() {}

    /**
     * Deployment related metrics - most of which are redundant
     * E.g. app/applicationName/tenantName/instanceName is already included in applicationId
     */
    public static final String APP = "app";
    public static final String APPLICATION_NAME = "applicationName";
    public static final String CLUSTER_NAME = "clustername";
    public static final String CLUSTER_ID = "clusterid";
    public static final String CLUSTER_TYPE = "clustertype";
    public static final String DEPLOYMENT_CLUSTER = "deploymentCluster";
    public static final String GROUP_ID = "groupId";
    public static final String INSTANCE = "instance";
    public static final String INSTANCE_NAME = "instanceName";
    public static final String TENANT_NAME = "tenantName";

    /**
     * State related dimensions - will always be the same value for a given snapshot
     */
    public static final String METRIC_TYPE = "metrictype";
    public static final String ORCHESTRATOR_STATE = "orchestratorState";
    public static final String ROLE = "role";
    public static final String STATE = "state";
    public static final String SYSTEM = "system";
    public static final String VESPA_VERSION = "vespaVersion";

    /**  Metric specific dimensions  **/
    public static final String ARCHITECTURE = "arch";
    public static final String AUTHZ_REQUIRED = "authz-required";
    public static final String HOME = "home";
    public static final String PORT = "port";
    public static final String SCHEME = "scheme";
    public static final String DRYRUN = "dryrun";
    public static final String VERSION = "version";

    public static final List<String> blocklistDimensions = List.of(
            APP,
            APPLICATION_NAME,
            CLUSTER_NAME,
            CLUSTER_ID,
            CLUSTER_TYPE,
            DEPLOYMENT_CLUSTER,
            GROUP_ID,
            INSTANCE,
            INSTANCE_NAME,
            TENANT_NAME,
            METRIC_TYPE,
            ORCHESTRATOR_STATE,
            ROLE,
            STATE,
            SYSTEM,
            VESPA_VERSION,
            ARCHITECTURE,
            AUTHZ_REQUIRED,
            HOME,
            PORT,
            SCHEME,
            DRYRUN,
            VERSION
    );

}
