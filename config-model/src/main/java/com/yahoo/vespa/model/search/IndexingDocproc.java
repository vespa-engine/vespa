package com.yahoo.vespa.model.search;

import com.yahoo.vespa.model.container.docproc.DocprocChain;

/**
 * Utility class to track configuration for which indexing docproc to use by a search cluster.
 */
public class IndexingDocproc {

    private String clusterName; // The name of the docproc cluster to run indexing, by config.
    private String chainName;

    private DocprocChain chain; // The actual docproc chain indexing for this.

    public boolean hasExplicitCluster() {
        return clusterName != null;
    }

    public boolean hasExplicitChain() {
        return chainName != null;
    }

    /**
     * Returns the name of the docproc cluster running indexing for this search cluster. This is derived from the
     * services file on initialization, this can NOT be used at runtime to determine indexing chain. When initialization
     * is done, the {@link #getServiceName()} method holds the actual indexing docproc chain object.
     *
     * @return the name of the docproc cluster associated with this
     */
    public String getClusterName(String searchClusterName) {
        return hasExplicitCluster() ? clusterName : searchClusterName + ".indexing";
    }

    public String getChainName() {
        return chainName;
    }

    public void setChainName(String name) {
        chainName = name;
    }

    /**
     * Sets the name of the docproc cluster running indexing for this search cluster. This is for initial configuration,
     * and will not reflect the actual indexing chain. See {@link #getClusterName} for more detail.
     *
     * @param name the name of the docproc cluster associated with this
     */
    public void setClusterName(String name) {
        clusterName = name;
    }

    public String getServiceName() {
        return chain.getServiceName();
    }

    /**
     * Sets the docproc chain that will be running indexing for this search cluster. This is set by the
     * {@link com.yahoo.vespa.model.content.Content} model during build.
     *
     * @param chain the chain that is to run indexing for this cluster
     */
    public void setChain(DocprocChain chain) { this.chain = chain; }

    public IndexingDocproc() {
        clusterName = null;
        chainName = null;
        chain = null;
    }

}
