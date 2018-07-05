// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.model.federation;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import net.jcip.annotations.Immutable;

import com.yahoo.search.federation.http.HTTPProviderSearcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Specifies how a http provider is to be set up.
 *
 * @author Tony Vaagenes
 */
@Immutable
public class HttpProviderSpec {
    public enum Type {
        vespa(com.yahoo.search.federation.vespa.VespaSearcher.class);

        Type(Class<? extends HTTPProviderSearcher> searcherClass) {
            className = searcherClass.getName();
        }

        final String className;
    }

    // The default connection parameter values come from the config definition
    public static class ConnectionParameters {
        public final Double readTimeout;
        public final Double connectionTimeout;
        public final Double connectionPoolTimeout;
        public final Integer retries;

        public ConnectionParameters(Double readTimeout, Double connectionTimeout,
                                    Double connectionPoolTimeout, Integer retries) {
            this.readTimeout = readTimeout;
            this.connectionTimeout = connectionTimeout;
            this.connectionPoolTimeout = connectionPoolTimeout;
            this.retries = retries;
        }
    }

    public static class Node {
        public final String host;
        public final int port;

        public Node(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public String toString() {
            return "Node{" +
                    "host='" + host + '\'' +
                    ", port=" + port +
                    '}';
        }
    }

    public final ConnectionParameters connectionParameters;

    public final Integer cacheSizeMB;

    public final String path;
    public final List<Node> nodes;
    public final String ycaApplicationId;
    public final Integer ycaCertificateTtl;
    public final Integer ycaRetryWait;
    public final Node ycaProxy;

    //TODO:remove this
    public final double cacheWeight;


    public static BundleInstantiationSpecification toBundleInstantiationSpecification(Type type) {
        return BundleInstantiationSpecification.getInternalSearcherSpecificationFromStrings(type.className, null);
    }

    public static boolean includesType(String typeString) {
        for (Type type : Type.values()) {
            if (type.name().equals(typeString)) {
                return true;
            }
        }
        return false;
    }

    public HttpProviderSpec(Double cacheWeight,
                            String path,
                            List<Node> nodes,
                            String ycaApplicationId,
                            Integer ycaCertificateTtl,
                            Integer ycaRetryWait,
                            Node ycaProxy,
                            Integer cacheSizeMB,
                            ConnectionParameters connectionParameters) {

        final double defaultCacheWeight = 1.0d;
        this.cacheWeight = (cacheWeight != null) ? cacheWeight : defaultCacheWeight;

        this.path = path;
        this.nodes = unmodifiable(nodes);
        this.ycaApplicationId = ycaApplicationId;
        this.ycaProxy = ycaProxy;
        this.ycaCertificateTtl = ycaCertificateTtl;
        this.ycaRetryWait = ycaRetryWait;
        this.cacheSizeMB = cacheSizeMB;

        this.connectionParameters = connectionParameters;
    }

    private List<HttpProviderSpec.Node> unmodifiable(List<HttpProviderSpec.Node> nodes) {
        return nodes == null ?
                Collections.<HttpProviderSpec.Node>emptyList() :
                Collections.unmodifiableList(new ArrayList<>(nodes));
    }
}
