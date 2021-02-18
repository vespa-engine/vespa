// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.google.common.base.Suppliers;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Report;
import com.yahoo.vespa.hosted.provision.node.Reports;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;

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
        lock = nodeRepository.nodes().lock(applicationId);
        this.application = nodeRepository.applications().require(applicationId);
    }

    /** Applies the json to the application and returns it. */
    public Application apply() {
        inspector.traverse((String name, Inspector value) -> {
            try {
                application = applyField(application, name, value, inspector);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Could not set field '" + name + "'", e);
            }
        });
        return application;
    }

    /** Returns the application in its current state (patch applied or not) */
    public Application application() { return application; }

    public Mutex lock() { return lock; }

    @Override
    public void close() {
        lock.close();
    }

    private Application applyField(Application application, String name, Inspector value, Inspector root) {
        switch (name) {
            case "currentTrafficFraction" :
                return application.with(application.status().withCurrentTrafficFraction(asDouble(value)));
            case "maxTrafficFraction" :
                return application.with(application.status().withMaxTrafficFraction(asDouble(value)));
            default :
                throw new IllegalArgumentException("Could not apply field '" + name + "' on an application: No such modifiable field");
        }
    }

    private Double asDouble(Inspector field) {
        if (field.type() != Type.DOUBLE)
            throw new IllegalArgumentException("Expected a DOUBLE value, got a " + field.type());
        return field.asDouble();
    }

}
