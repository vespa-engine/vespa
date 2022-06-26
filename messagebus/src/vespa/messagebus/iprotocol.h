// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blobref.h"
#include "routable.h"
#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vespa/vespalib/component/version.h>

namespace mbus {

/**
 * A protocol has support for decoding raw data into routable objects and for
 * instantiating routing policy objects. Each protocol has a name. The name of
 * a protocol is global across implementations. Protocols with the same name
 * are expected to know how to encode and decode the same set of routables and
 * also have support for the same set of routing policies.
 */
class IProtocol {
protected:
    IProtocol() = default;
public:
    IProtocol(const IProtocol &) = delete;
    IProtocol & operator = (const IProtocol &) = delete;
    virtual ~IProtocol() {}

    /**
     * Convenience typedef for an auto pointer to an IProtocol object.
     */
    typedef std::unique_ptr<IProtocol> UP;

    /**
     * Convenience typedef for a shared pointer to a IProtocol object.
     */
    typedef std::shared_ptr<IProtocol> SP;

    /**
     * Obtain the name of this protocol.
     *
     * @return Protocol name.
     */
    virtual const string & getName() const = 0;

    /**
     * Instantiate a routing policy based on its name and parameter. Routing
     * policies are created my messagebus based on the selector string. A
     * selector path element using a custom routing policy is on the form
     * '[name:param]'. The semantics of the parameter is up to the routing
     * policy. It could be a simple value or even a config id.
     *
     * @param name Routing policy name (local to this protocol).
     * @param param Ppolicy specific parameter.
     * @return A newly created routing policy.
     */
    virtual IRoutingPolicy::UP createPolicy(const string &name, const string &param) const = 0;

    /**
     * Encodes the protocol specific data of a routable into a byte array.
     *
     * Errors should be catched and logged by the encode implementation and
     * an empty blob should be returned. This will make messagebus generate
     * a reply to send back to the client.
     *
     * @param version  The version to encode for.
     * @param routable The routable to encode.
     * @return The encoded data.
     */
    virtual Blob encode(const vespalib::Version &version, const Routable &routable) const = 0; // throw()

    /**
     * Decodes the protocol specific data into a routable of the correct type.
     *
     * Errors should be catched and logged by the decode implementation, and
     * a null pointer should be returned. This will make messagebus generate
     * a reply to send back to the client.
     *
     * @param version The version of the serialized routable.
     * @param payload The payload to decode from.
     * @return The decoded routable.
     */
    virtual Routable::UP decode(const vespalib::Version &version, BlobRef data) const = 0; // throw()
};

} // namespace mbus

