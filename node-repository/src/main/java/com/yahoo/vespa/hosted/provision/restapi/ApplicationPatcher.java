// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.BcpGroupInfo;
import com.yahoo.vespa.hosted.provision.applications.Cluster;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;

/**
 * A class which can take a partial JSON node/v2 application JSON structure and apply it to an application object.
 * This is a one-time use object.
 *
 * @author bratseth
 */
public class ApplicationPatcher implements AutoCloseable {

    private final Inspector inspector;

    private final Mutex lock;
    private Application application;

    public ApplicationPatcher(InputStream json, ApplicationId applicationId, NodeRepository nodeRepository) {
        try {
            this.inspector = SlimeUtils.jsonToSlime(IOUtils.readBytes(json, 1000 * 1000)).get();
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading request body", e);
        }
        // Use same timeout for acquiring lock as client timeout for patch request
        this.lock = nodeRepository.applications().lock(applicationId, Duration.ofSeconds(30));
        try {
            this.application = nodeRepository.applications().require(applicationId);
        }
        catch (RuntimeException e) {
            lock.close();
            throw e;
        }
    }

    /** Applies the json to the application and returns it. */
    public Application apply() {
        inspector.field("currentReadShare").ifValid(v -> application = application.with(application.status().withCurrentReadShare(asDouble(v))));
        inspector.field("maxReadShare").ifValid(v -> application = application.with(application.status().withMaxReadShare(asDouble(v))));
        inspector.field("clusters").ifValid(cluster -> application = applyClustersField(cluster));
        return application;
    }

    /** Returns the application in its current state (patch applied or not) */
    public Application application() { return application; }

    public Mutex lock() { return lock; }

    @Override
    public void close() {
        lock.close();
    }

    private Application applyClustersField(Inspector clusters) {
        clusters.traverse((String key, Inspector cluster) -> application = applyClusterField(key, cluster));
        return application;
    }

    private Application applyClusterField(String id, Inspector clusterObject) {
        Optional<Cluster> cluster = application.cluster(ClusterSpec.Id.from(id));
        if (cluster.isEmpty()) return application;
        Inspector bcpGroupInfoObject = clusterObject.field("bcpGroupInfo");
        if ( ! bcpGroupInfoObject.valid()) return application;
        double queryRate = bcpGroupInfoObject.field("queryRate").asDouble();
        double growthRateHeadroom = bcpGroupInfoObject.field("growthRateHeadroom").asDouble();
        double cpuCostPerQuery = bcpGroupInfoObject.field("cpuCostPerQuery").asDouble();
        return application.with(cluster.get().with(new BcpGroupInfo(queryRate, growthRateHeadroom, cpuCostPerQuery)));
    }

    private Double asDouble(Inspector field) {
        if (field.type() != Type.DOUBLE)
            throw new IllegalArgumentException("Expected a DOUBLE value, got a " + field.type());
        return field.asDouble();
    }

}
