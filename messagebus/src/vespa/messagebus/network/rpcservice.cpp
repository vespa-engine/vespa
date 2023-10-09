// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcservice.h"
#include "rpcnetwork.h"

namespace mbus {

RPCService::RPCService(const Mirror &mirror, const string &pattern) :
    _serviceName(),
    _connectionSpec()
{
    if (pattern.find("tcp/") == 0) {
        size_t pos = pattern.find_last_of('/');
        if (pos != string::npos && pos < pattern.size() - 1) {
            RPCServiceAddress test(pattern, pattern.substr(0, pos));
            if ( ! test.isMalformed()) {
                _serviceName = pattern;
                _connectionSpec = pattern.substr(0, pos);
            }
        }
    } else {
        Mirror::SpecList addressList = mirror.lookup(pattern);
        if (!addressList.empty()) {
            assert(addressList.size() == 1); //TODO URGENT remove assert after a few factory runs.
            const auto &entry = addressList.front();
            _serviceName = entry.first;
            _connectionSpec = entry.second;
        }
    }
}

RPCService::~RPCService() = default;

RPCServiceAddress::UP
RPCService::make_address()
{
    if ( !_serviceName.empty()) {
        return std::make_unique<RPCServiceAddress>(_serviceName, _connectionSpec);
    }
    return RPCServiceAddress::UP();
}


} // namespace mbus
