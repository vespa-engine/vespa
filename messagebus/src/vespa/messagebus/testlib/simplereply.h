// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "simplemessage.h"
#include <vespa/messagebus/reply.h>

namespace mbus {

class SimpleReply : public Reply
{
private:
    string _value;
    SimpleReply &operator=(const SimpleReply &);
public:
    using UP = std::unique_ptr<SimpleReply>;
    SimpleReply(const string &str);
    virtual ~SimpleReply();
    void setValue(const string &value);
    const string &getValue() const;
    const string & getProtocol() const override;
    uint32_t getType() const override;

    uint8_t priority() const override { return 8; }
};

} // namespace mbus
