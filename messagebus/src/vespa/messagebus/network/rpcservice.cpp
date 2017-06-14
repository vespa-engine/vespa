// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpcservice.h"
#include "rpcnetwork.h"

namespace mbus {

RPCService::RPCService(const Mirror &mirror,
                       const string &pattern) :
    _mirror(mirror),
    _pattern(pattern),
    _addressIdx(random()),
    _addressGen(0),
    _addressList()
{ }

RPCService::~RPCService() {}

RPCServiceAddress::UP
RPCService::resolve()
{
    if (_pattern.find("tcp/") == 0) {
        size_t pos = _pattern.find_last_of('/');
        if (pos != string::npos && pos < _pattern.size() - 1) {
            RPCServiceAddress::UP ret(new RPCServiceAddress(
                            _pattern,
                            _pattern.substr(0, pos)));
            if (!ret->isMalformed()) {
                return ret;
            }
        }
    } else {
        if (_addressGen != _mirror.updates()) {
            _addressGen = _mirror.updates();
            _addressList = _mirror.lookup(_pattern);
        }
        if (!_addressList.empty()) {
            _addressIdx = (_addressIdx + 1) % _addressList.size();
            const AddressList::value_type &entry = _addressList[_addressIdx];
            return RPCServiceAddress::UP(new RPCServiceAddress(
                            entry.first,
                            entry.second));
        }
    }
    return RPCServiceAddress::UP();
}


} // namespace mbus
