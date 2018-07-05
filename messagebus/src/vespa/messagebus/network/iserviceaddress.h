// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace mbus {

/**
 * This interface represents an abstract network service; i.e. somewhere to send messages. An instance of this is
 * retrieved by calling {@link Network#lookup(String)}.
 *
 * @author Simon Thoresen Hult
 * @version $Id$
 */
class IServiceAddress {
public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<IServiceAddress> UP;

    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~IServiceAddress() { }
};

} // namespace mbus

