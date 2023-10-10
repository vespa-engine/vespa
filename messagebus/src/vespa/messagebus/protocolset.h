// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>
#include "iprotocol.h"

namespace mbus {

/**
 * This class is used to bundle a set of IProtocol objects to be
 * supported by a MessageBus instance.
 **/
class ProtocolSet
{
private:
    std::vector<IProtocol::SP> _vector;

public:
    /**
     * Create an empty ProtocolSet.
     **/
    ProtocolSet();

    /**
     * Add a Protocol to this set.
     *
     * @return this object, to allow chaining
     * @param protocol the IProtocol we want to add
     **/
    ProtocolSet &add(IProtocol::SP protocol);

    /**
     * Check if this set is empty
     *
     * @return true if this set is empty
     **/
    bool empty() const;

    /**
     * Extract a single protocol from this set. This will remove the
     * protocol from the set. If the set is empty, a shared pointer to
     * 0 will be returned.
     *
     * @return the extracted IProtocol
     **/
    IProtocol::SP extract();
};

} // namespace mbus

