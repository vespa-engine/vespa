// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "reply.h"

namespace mbus {

/**
 * A concrete reply that contains no protocol-specific data. This is needed to
 * enable messagebus to reply to messages that result in an error. It may also
 * be used by the application for ack type replies. Objects of this class will
 * identify as type 0, which is reserved for this use. Also note that whenever a
 * protocol-specific reply encodes to an empty blob it will be decoded to an
 * EmptyReply at its network peer.
 */
class EmptyReply : public Reply {
public:
    EmptyReply();
    const string & getProtocol() const override;
    uint32_t getType() const override;
    uint8_t priority() const override { return 8; }
};

}
