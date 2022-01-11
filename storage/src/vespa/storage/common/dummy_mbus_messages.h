// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/message.h>

/**
 * Dummy-implementation of mbus::Message and mbus::Reply to be used when interacting with
 * MessageBus IThrottlePolicy subclasses, as these expect message instances as parameters.
 */

namespace storage {

template <typename Base>
class DummyMbusMessage : public Base {
    static const mbus::string NAME;
public:
    const mbus::string& getProtocol() const override { return NAME; }
    uint32_t getType() const override { return 0x1badb007; }
    uint8_t priority() const override { return 255; }
};

template <typename Base>
const mbus::string DummyMbusMessage<Base>::NAME = "FooBar";

class DummyMbusRequest final : public DummyMbusMessage<mbus::Message> {
public:
    // getApproxSize() returns 1 by default.
    // Approximate size of messages allowed by throttle policy is implicitly added to
    // internal StaticThrottlePolicy pending size tracking and associated with the
    // internal mbus context of the message.
    // Since we have no connection between the request and reply instances used when
    // interacting with the policy, we have to make sure they cancel each other out
    // (i.e. += 0, -= 0).
    // Not doing this would cause the StaticThrottlePolicy to keep adding a single byte
    // of pending size for each message allowed by the policy.
    uint32_t getApproxSize() const override { return 0; }
};

class DummyMbusReply final : public DummyMbusMessage<mbus::Reply> {};

}
