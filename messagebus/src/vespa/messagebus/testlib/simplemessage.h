// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/message.h>

namespace mbus {

class SimpleMessage : public Message {
private:
    string _value;
    bool        _hasSeqId;
    uint64_t    _seqId;

public:
    SimpleMessage(const string &str);
    SimpleMessage(const string &str, bool hasSeqId, uint64_t seqId);
    ~SimpleMessage();

    void setValue(const string &value);
    const string &getValue() const;
    int getHash() const;
    const string & getProtocol() const;
    uint32_t getType() const;
    bool hasSequenceId() const;
    uint64_t getSequenceId() const;
    uint32_t getApproxSize() const;

    uint8_t priority() const { return 8; }

    string toString() const { return _value; }
};

} // namespace mbus

