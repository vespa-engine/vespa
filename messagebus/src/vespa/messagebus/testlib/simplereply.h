// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vespa/messagebus/reply.h>
#include "simplemessage.h"

namespace mbus {

class SimpleReply : public Reply
{
private:
    string _value;
    SimpleReply &operator=(const SimpleReply &);
public:
    typedef std::unique_ptr<SimpleReply> UP;
    SimpleReply(const string &str);
    virtual ~SimpleReply();
    void setValue(const string &value);
    const string &getValue() const;
    virtual const string & getProtocol() const override;
    virtual uint32_t getType() const override;

    uint8_t priority() const override { return 8; }
};

} // namespace mbus

