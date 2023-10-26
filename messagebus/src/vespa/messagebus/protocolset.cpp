// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "protocolset.h"

namespace mbus {

ProtocolSet::ProtocolSet()
    : _vector()
{ }

ProtocolSet &
ProtocolSet::add(IProtocol::SP protocol)
{
    _vector.push_back(protocol);
    return *this;
}

bool
ProtocolSet::empty() const
{
    return _vector.empty();
}

IProtocol::SP
ProtocolSet::extract()
{
    if (_vector.empty()) {
        return IProtocol::SP();
    }
    IProtocol::SP ret = _vector.back();
    _vector.pop_back();
    return ret;
}

} // namespace mbus
