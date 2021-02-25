// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

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
        this.lock = nodeRepository.nodes().lock(applicationId);
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
            case "currentReadShare" :
                return application.with(application.status().withCurrentReadShare(asDouble(value)));
            case "maxReadShare" :
                return application.with(application.status().withMaxReadShare(asDouble(value)));
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
