// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/messagebus/network/rpcnetworkparams.h>

namespace vesparoute {

/**
 * All parameters for application is contained in this class to simplify the api for parsing them.
 */
class Params {
public:
    /** Constructs a new parameter object. */
    Params();

    /** Destructor. Frees any allocated resources. */
    virtual ~Params();

    /** Returns the rpc network params object. */
    mbus::RPCNetworkParams &getRPCNetworkParams() { return _rpcParams; }

    /** Returns a const reference to the rpc network params object. */
    const mbus::RPCNetworkParams &getRPCNetworkParams() const { return _rpcParams; }

    /** Returns the list of hops to print. */
    std::vector<std::string> &getHops() { return _hops; }

    /** Returns a const reference to the list of hops to print. */
    const std::vector<std::string> &getHops() const { return _hops; }

    /** Returns the list of routes to print. */
    std::vector<std::string> &getRoutes() { return _routes; }

    /** Returns a const reference the list of routes to print. */
    const std::vector<std::string> &getRoutes() const { return _routes; }

    /** Sets the config id to use for document types. */
    void
    setDocumentTypesConfigId(const std::string &configId)
    {
        _documentTypesConfigId = configId;
    }

    /** Returns the config id to use for the document manager. */
    const std::string &
    getDocumentTypesConfigId()
    {
        return _documentTypesConfigId;
    }

    /** Sets the config id to use for routing. */
    void setRoutingConfigId(const std::string &configId) { _routingConfigId = configId; }

    /** Returns the config id to use for routing. */
    const std::string &getRoutingConfigId() { return _routingConfigId; }

    /** Sets the name of the protocol whose routing table to use. */
    void setProtocol(const std::string &protocol) { _protocol = protocol; }

    /** Returns the name of the protocol whose routing table to use. */
    const std::string &getProtocol() const { return _protocol; }

    /** Sets wether or not to print all hops. */
    void setListHops(bool lst) { _lstHops = lst; }

    /** Returns wether or not to print all hops. */
    bool getListHops() const { return _lstHops; }

    /** Sets wether or not to print all routes. */
    void setListRoutes(bool lst) { _lstRoutes = lst; }

    /** Returns wether or not to print all routes. */
    bool getListRoutes() const { return _lstRoutes; }

    /** Sets wether or not to print all services. */
    void setListServices(bool lst) { _lstServices = lst; }

    /** Returns wether or not to print all services. */
    bool getListServices() const { return _lstServices; }

    /** Sets wether or not to print the full routing table content. */
    void setDump(bool dump) { _dump = dump; }

    /** Returns wether or not to print the full routing table content. */
    bool getDump() const { return _dump; }

    /** Sets wether or not to verify service names. */
    void setVerify(bool verify) { _verify = verify; }

    /** Returns wether or not to verify service names. */
    bool getVerify() const { return _verify; }

    const std::string & getSlobrokConfigId() const { return _slobrokConfigId; }
    void setSlobrokId(const std::string & id) { _slobrokConfigId = id; }

private:
    mbus::RPCNetworkParams   _rpcParams;
    std::vector<std::string> _hops;
    std::vector<std::string> _routes;
    std::string              _documentTypesConfigId;
    std::string              _routingConfigId;
    std::string              _protocol;
    std::string              _slobrokConfigId;
    bool                     _lstHops;
    bool                     _lstRoutes;
    bool                     _lstServices;
    bool                     _dump;
    bool                     _verify;
};

}

