// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.api.ServiceInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Representation of a process which runs a service
 *
 * @author gjoranv
 */
public interface Service extends ConfigProducer, NetworkPortRequestor {

    /**
     * Services that should be started by config-sentinel must return
     * a non-empty value. The returned value will be used in config-sentinel
     * configuration.
     */
    Optional<String> getStartupCommand();

    // environment variables specific for this service:
    Map<String, Object> getEnvVars();

    /**
     * Services that wish that a command should be run before shutdown
     * should return the command here. The command will be executed
     * by the config sentinel before sending SIGTERM to the service.
     * The command is executed without a timeout.
     */
    Optional<String> getPreShutdownCommand();

    /** Returns a PortsMeta object, giving access to more information about the different ports of this service. */
    PortsMeta getPortsMeta();

    /** Returns the physical host resource on which this service runs. */
    HostResource getHost();

    /**
     * Get meta information about service.
     * @return an instance of {@link com.yahoo.config.model.api.ServiceInfo}
     */
    ServiceInfo getServiceInfo();

    /** Returns the hostname on which this service runs. */
    String getHostName();

    /** Optional JVM execution options for this service */
    String getJvmOptions();

    /**
     * Computes and returns the i'th port for this service, based on this Service's baseport.
     *
     * @param i the offset from 'basePort' of the port to return
     * @return the i'th port relative to the base port
     * @throws IllegalStateException if i is out of range
     */
    int getRelativePort(int i);

    /**
     * Gets a service property value mapped to the given key
     * as a String, or the value in <code>defStr</code> if no such key exists.
     *
     * @param key    a key used for lookup in the service properties
     * @param defStr default String value returned if no value for key found
     * @return the associated String value for the given key
     */
    String getServicePropertyString(String key, String defStr);

    int getHealthPort();

    /** Returns a HashMap of default dimensions for metrics. */
    HashMap<String,String> getDefaultMetricDimensions();

    /** Returns the Affinity of this service if it has. */
    Optional<Affinity> getAffinity();

}
