// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rpcserviceaddress.h"

namespace mbus {

RPCServiceAddress::RPCServiceAddress(const string &serviceName,
                                     const string &connectionSpec) :
    _serviceName(serviceName),
    _sessionName(""),
    _connectionSpec(connectionSpec),
    _target()
{
    size_t pos = serviceName.find_last_of('/');
    if (pos != string::npos) {
        _sessionName = serviceName.substr(pos + 1);
    }
}

RPCServiceAddress::~RPCServiceAddress() = default;

bool
RPCServiceAddress::isMalformed()
{
    if (_serviceName.empty()) {
        return true; // no service
    }
    if (_sessionName.empty()) {
        return true; // no session
    }
    if (_connectionSpec.empty()) {
        return true; // no spec
    }
    size_t prefixPos = _connectionSpec.find("tcp/");
    if (prefixPos != 0) {
        return true; // no prefix
    }
    size_t portPos = _connectionSpec.find_first_of(':', prefixPos);
    if (portPos == string::npos) {
        return true; // not colon
    }
    if (portPos == prefixPos + 4) {
        return true; // no address
    }
    if (portPos == _connectionSpec.size() - 1) {
        return true; // no port
    }
    return false;
}

} // namespace mbus
