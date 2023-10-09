// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/common.h>
#include <vector>

namespace mbus {

/**
 * An Identity object is a simple value object containing information
 * about the identity of a Network object within the vespa cluster. It
 * contains the service name prefix used when registering
 * sessions. This class also has some static utility methods for
 * general service name manipulation.
 **/
class Identity
{
private:
    string _hostname;
    string _servicePrefix;

public:
    /**
     * Use config to resolve the identity for the given config
     * id. This is intended to be done once at program
     * startup. Changing the identity of a service requires
     * restart. This method will not mask config exceptions.
     *
     * @return identity for the given config id
     * @param configId application config id
     **/
    Identity(const string &configId);
    ~Identity();

    /**
     * Obtain the hostname held by this object.
     *
     * @return hostname
     **/
    const string &getHostname() const { return _hostname; }

    /**
     * Obtain the service prefix held by this object.
     *
     * @return service prefix
     **/
    const string &getServicePrefix() const { return _servicePrefix; }

    /**
     * Split a service name into its path elements.
     *
     * @return service name path elements
     * @param name the service name
     **/
    static std::vector<string> split(const string &name);
};

} // namespace mbus
