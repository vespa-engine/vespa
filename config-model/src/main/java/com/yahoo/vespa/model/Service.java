// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.api.ServiceInfo;

import java.util.HashMap;
import java.util.Optional;

/**
 * Representation of a process which runs a service
 *
 * @author gjoranv
 */
public interface Service extends ConfigProducer {

    /**
     * Services that should be started by config-sentinel must return
     * non-null. The returned value will be used in config-sentinel
     * configuration.
     * TODO: Should change this to Optional of String
     */
    String getStartupCommand();

    /**
     * Services that wish that a command should be run before shutdown
     * should return the command here. The command will be executed
     * by the config sentinel before sending SIGTERM to the service.
     * The command is executed without a timeout.
     */
    Optional<String> getPreShutdownCommand();

    /**
     * Tells if this service should be autostarted by
     * config-sentinel. Returned value will be used to configure the
     * config-sentinel.
     */
    boolean getAutostartFlag();

    /**
     * Tells if this service should be autorestarted by
     * config-sentinel. Returned value will be used to configure the
     * config-sentinel.
     */
    boolean getAutorestartFlag();

    /**
     * Returns the type of service. E.g. the class-name without the
     * package prefix.
     */
    String getServiceType();

   /**
     * Returns the name that identifies this service for the config-sentinel.
     */
    String getServiceName();

    /**
     * Returns the desired base port for this service, or '0' if this
     * service should use the default port allocation mechanism.
     *
     * @return The desired base port for this service.
     */
    int getWantedPort();

    /**
     * Returns true if the desired base port (returned by
     * getWantedPort()) for this service is the only allowed base
     * port.
     *
     * @return true if this Service requires the wanted base port.
     */
    boolean requiresWantedPort();

    /**
     * Returns the number of ports needed by this service.
     */
    int getPortCount();

    /**
     * Returns a PortsMeta object, giving access to more information
     * about the different ports of this service.
     */
    PortsMeta getPortsMeta();

    /**
     * @return the physical host on which this service runs.
     */
    Host getHost();

    /**
     * Get meta information about service.
     * @return an instance of {@link com.yahoo.config.model.api.ServiceInfo}
     */
    ServiceInfo getServiceInfo();

    /**
     * @return The hostname on which this service runs.
     */
    String getHostName();

    /** Optional JVM execution args for this service */
    String getJvmArgs();

    /**
     * Computes and returns the i'th port for this service, based on
     * this Service's baseport.
     *
     * @param i The offset from 'basePort' of the port to return
     * @return the i'th port relative to the base port.
     * @throws IllegalStateException if i is out of range.
     */
    int getRelativePort(int i);

    /**
     * Gets a service property value mapped to the given key
     * as a String, or the value in <code>defStr</code> if no such key exists.
     *
     * @param key    a key used for lookup in the service properties
     * @param defStr default String value returned if no value for key found
     * @return the associated String value for the given key, or
     */
    String getServicePropertyString(String key, String defStr);

    int getHealthPort();

    /**
     *
     * @return HashMap of default dimensions for metrics.
     */
    HashMap<String,String> getDefaultMetricDimensions();

    /**
     * Return the Affinity of this service if it has.
     *
     * @return The {@link com.yahoo.vespa.model.Affinity} for this service.
     */
    Optional<Affinity> getAffinity();

}
