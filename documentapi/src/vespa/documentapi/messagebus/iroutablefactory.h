// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/util/bytebuffer.h>
#include <vespa/messagebus/routable.h>
#include <vespa/vespalib/component/version.h>
#include <vespa/vespalib/util/growablebytebuffer.h>

namespace documentapi {

/**
 * This interface defines the necessary methods of a routable factory that can be plugged into a {@link
 * DocumentProtocol} using the {@link DocumentProtocol#putRoutableFactory(int, RoutableFactory,
 * com.yahoo.component.VersionSpecification)} method. Consider extending {@link DocumentMessageFactory} or
 * {@link DocumentReplyFactory} instead of implementing this interface.
 *
 * Notice that no routable type is passed to the {@link #decode(ByteBuffer)} method, so you may NOT share a
 * factory across multiple routable types. To share serialization logic between factory use a common
 * superclass or composition with a common serialization utility.
 */
class IRoutableFactory {
protected:
    IRoutableFactory() = default;
public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<IRoutableFactory> UP;
    typedef std::shared_ptr<IRoutableFactory> SP;

    IRoutableFactory(const IRoutableFactory &) = delete;
    IRoutableFactory & operator = (const IRoutableFactory &) = delete;
    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~IRoutableFactory() { }

    /**
     * This method encodes the content of the given routable into a byte buffer that can later be decoded
     * using the {@link #decode(ByteBuffer)} method.
     *
     * This method is NOT exception safe. Return false to signal failure.
     *
     * @param obj The routable to encode.
     * @param out The buffer to write into.
     * @return True if the routable could be encoded.
     */
    virtual bool encode(const mbus::Routable &obj,
                        vespalib::GrowableByteBuffer &out) const = 0;

    /**
     * This method decodes the given byte bufer to a routable.
     *
     * This method is NOT exception safe. Return null to signal failure.
     *
     * @param in The buffer to read from.
     * @param loadTypes The set of configured load types.
     * @return The decoded routable.
     */
    virtual mbus::Routable::UP decode(document::ByteBuffer &in) const = 0;
};

}

