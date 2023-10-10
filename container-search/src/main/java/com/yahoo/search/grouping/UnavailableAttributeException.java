// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping;

/**
 * This exception is thrown by the {@link GroupingValidator} if it a {@link GroupingRequest} contains a reference to an
 * unavailable attribute.
 *
 * @author Simon Thoresen Hult
 */
@SuppressWarnings("serial")
public class UnavailableAttributeException extends RuntimeException {

    private final String clusterName;
    private final String attributeName;

    /**
     * Constructs a new instance of this class.
     *
     * @param clusterName   The name of the cluster for which the request is illegal.
     * @param attributeName The name of the attribute which is referenced but not available.
     */
    public UnavailableAttributeException(String clusterName, String attributeName) {
        super("Grouping request references attribute '" + attributeName + "' which is not available " +
              "in cluster '" + clusterName + "'.");
        this.clusterName = clusterName;
        this.attributeName = attributeName;
    }

    /**
     * Returns the name of the cluster for which the request is illegal.
     *
     * @return The cluster name.
     */
    public String getClusterName() {
        return clusterName;
    }

    /**
     * Returns the name of the attribute which is referenced but not available.
     *
     * @return The attribute name.
     */
    public String getAttributeName() {
        return attributeName;
    }
}
