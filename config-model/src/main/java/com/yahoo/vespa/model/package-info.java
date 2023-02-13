// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
    Provides the classes for the Vespa config model framework.

    <p>The {@link com.yahoo.vespa.model.VespaModel VespaModel} class
    is the natural starting point. It reads the user-defined
    application specification, handles <a
    href="#plugin_loading">plugin loading</a> and currently
    instantiates one {@link com.yahoo.config.model.ApplicationConfigProducerRoot Vespa}
    object. VespaModel is the root node in a tree of {@link
com.yahoo.config.model.producer.TreeConfigProducer
    TreeConfigProducers} that is built from the structure of the
    user's specification. In a future version, the VespaModel can
    contain multiple Vespa instances, each built from a separate user
    specification (currently called 'services.xml').
    </p>

    <p>Each TreeConfigProducer in the tree represents an actual
    service or another logical unit in the Vespa system. An example of
    a logical unit is a cluster that holds a set of services. Each
    child class of {@link com.yahoo.config.model.producer.TreeConfigProducer
    TreeConfigProducer} can contain hard-wired config that should
    be delivered to the Vespa unit it represents, and its children. It
    can also keep track of the status of the unit.
    </p>

    <p>A service that runs on a hardware host is always represented by
    an {@link com.yahoo.vespa.model.AbstractService  AbstractService}
    object, containing the command that will be used to start the
    service, which host it is running on, and the ports that it
    uses. Each hardware host in the Vespa system is represented by a
    {@link com.yahoo.vespa.model.Host Host} object, and the set of
    hosts is handled by the {@link com.yahoo.vespa.model.HostSystem
    HostSystem}. Each Host is responsible for avoiding port collisions
    between services, see <a href="#port_allocation">port
    allocation</a>.
    </p>

    <h2>Config Generation</h2>

    <p>The method {@link
    com.yahoo.vespa.model.VespaModel#getConfig(com.yahoo.config.ConfigInstance.Builder, String)
    VespaModel.getConfig} looks up the ConfigProducer with the config
    ID that config is requested for. The composition of the actual
    config starts from the root node of the ConfigProducer tree, which
    is always an instance of the {@link com.yahoo.config.model.ApplicationConfigProducerRoot
    Vespa} class, and traverses each level of the tree back down to
    the ConfigProducer that got the first call from the root node.
    This is handled in such a way
    that config from the root node gets the lowest priority, and the
    ConfigProducer itself has the highest priority when the same
    parameter is given different values in the path down the tree.</p>

    <p>User defined configuration can be embedded in the service setup
    file in the application specification. Currently this is done by adding
    &lt;config&gt; tags at the desired position in the file named
    'services.xml', where each position corresponds to a
    ConfigProducer. These config values have a higher priority than
    the default config returned from the same method. However, it can be overridden by the config
    from a ConfigProducer at a lower level, both by its getConfig
    method and by user defined config.
    </p>

    <h3>Example:</h3>
    <p>
    Say we have a config named 'sample' with an integer parameter
    named 'v'. If the VespaModel root node's {@link
    com.yahoo.vespa.model.VespaModel#getConfig(com.yahoo.config.ConfigInstance.Builder,String)
    getConfig(builder, configid)} method returns a hardcoded value of
    'v=2' for that parameter, this becomes the default value for all
    ConfigProducers when asking for the 'sample' config. Now, let's
    assume that we need the 'sample' config for a ConfigProducer of
    class 'Grandchild', which has a configId
    'grandchild_0'. grandchild_0's parent in the ConfigProducer tree
    is a ConfigProducer of class 'Child' and configId 'child_0' which
    is a direct child of the Vespa root node:
    </p>

    <p>The initial step when retrieving a config is always a call to
    {@link
    com.yahoo.vespa.model.VespaModel#getConfig(com.yahoo.config.ConfigInstance.Builder,String)
    VespaModel.getConfig(builder, configId}. Here, the call
    could look like this:
    VespaModel.getConfig(builder, &quot;grandchild_0&quot;).
    This triggers a call to the {@link
    com.yahoo.config.model.producer.TreeConfigProducer#cascadeConfig(com.yahoo.config.ConfigInstance.Builder)}) TreeConfigProducer.cascadeConfig} method for
    grandchild_0 which calls the same method in child_0, and finally
    in the VespaModel root node, where the {@link
    com.yahoo.vespa.model.VespaModel#getConfig(com.yahoo.config.ConfigInstance.Builder,String)
    getConfig (name, namespace)} method returns the value 'v=2' as
    previously mentioned. This value might be overridden on the
    traversal back down in the tree, first in child_0, which could
    return the value 'v=1'. Now, if the user specification for child_0
    contains the value 'v=0', this overrides the previous values. The
    same happens for grandchild_0: if there is a value returned from
    the getConfig() method, this overrides the value from child_0, and
    if there is a value from the user specification for grandchild_0,
    that will always become the final result.
    </p>


    <h2 id="plugin_loading">Plugin Loading</h2>

    <p>Each highest-level node in the setup file from the user's
    application specification corresponds to a {@link
com.yahoo.config.model.builder.xml.ConfigModelBuilder ConfigModelBuilder}. The
    builders are loaded when the system is started. Each builder produce
    a {@link com.yahoo.config.model.ConfigModel ConfigModel}. The model can depend
    on other models by having them injected in its constructor. This ensures
    that the builders are invoked in the correct order as well.
    In its build method, the builder is responsible for building all its
    ConfigProducers, and linking them to the parent ConfigProducer
    given as input argument.
    </p>

    <p>The built models are given to other models that depends on it.
    </p>

    <h3>Important notes for plugin developers:</h3>
    <ul>

    <li>The constructors of all child classes of {@link
com.yahoo.config.model.producer.TreeConfigProducer
    TreeConfigProducer} should throw a new 'RuntimeException' upon
    errors in xml or other initialization problems. This allows the
    exception to be nested upwards, adding valuable information from
    each level in the ConfigProducer tree to the error message output
    to the user. The exception should contain detailed information
    about the error that occurred.
    </li>

    <li>The plugins are not allowed to put any constraints on the
    contents of the hosts specification file (currently named
    'hosts.xml'), such as demanding special hostnames for
    different service types. This file belongs solely to the
    vespamodel framework.
    </li>
    </ul>


    <h2 id="port_allocation">Port Allocation</h2>

    <p>Each {@link com.yahoo.vespa.model.Host Host} has an available
    dynamic port range running from {@link
    com.yahoo.vespa.model.HostPorts#BASE_PORT BASE_PORT} (currently 19100)
    with {@link com.yahoo.vespa.model.HostPorts#MAX_PORTS MAX_PORTS}
    (currently 799) ports upwards. When an instance of a subclass of
    {@link com.yahoo.vespa.model.AbstractService  AbstractService} is
    assigned to a host, it is given the lowest available base port in
    this range. The service owns a continuous port range of {@link
    com.yahoo.vespa.model.Service#getPortCount Service.getPortCount}
    ports upwards from the base port.
    </p>

    <p>The base port for a specific service instance on a host is
    decided by {@link
    com.yahoo.vespa.model.AbstractService #getInstanceWantedPort
    AbstractService.getInstanceWantedPort}. The most important aspects
    are described below:
    </p>

    <p>It is not possible to reserve a certain port inside the dynamic
    range, but a service can specify that it wants a base port outside
    the range by overriding the {@link
    com.yahoo.vespa.model.Service #getWantedPort Service.getWantedPort}
    method. If the service type is required to run with the specified
    base port, it must also override the {@link
    com.yahoo.vespa.model.Service #requiresWantedPort
    Service.requiresWantedPort}. The user specified port number
    returned from {@link com.yahoo.vespa.model.Service #getWantedPort
    getWantedPort} applies to the first instance of that specific
    subclass on each host, and the next instance on the same host
    <em>must</em> have its baseport specified by the 'baseport' attribute
    in 'services.xml'
    </p>

    <p>The user-defined application specification can also give a
    required base port for each individual service. Currently this is
    done by adding a 'baseport' attribute to the service's tag in the
    file named 'hosts.xml'. If the port is not available, an
    exception will be thrown.
    </p>

*/
@ExportPackage
package com.yahoo.vespa.model;

import com.yahoo.osgi.annotation.ExportPackage;
