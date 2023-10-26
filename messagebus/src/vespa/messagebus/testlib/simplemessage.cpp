// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplemessage.h"
#include "simpleprotocol.h"

namespace mbus {

SimpleMessage::SimpleMessage(const string &str) :
    Message(),
    _value(str),
    _hasSeqId(false),
    _seqId(0)
{ }

SimpleMessage::SimpleMessage(const string &str, bool hasSeqId, uint64_t seqId) :
    Message(),
    _value(str),
    _hasSeqId(hasSeqId),
    _seqId(seqId)
{ }

SimpleMessage::~SimpleMessage() { }

void
SimpleMessage::setValue(const string &value)
{
    _value = value;
}

const string &
SimpleMessage::getValue() const
{
    return _value;
}

int
SimpleMessage::getHash() const
{
    int hash = 0;
    string str = _value;
    for (uint32_t i = 0; i < str.size(); ++i) {
        hash += (hash << 9) + (hash >> 7) + (str[i] << 5) + (str[i] >> 3);
    }
    return hash;
}

const string &
SimpleMessage::getProtocol() const
{
    return SimpleProtocol::NAME;
}

uint32_t
SimpleMessage::getType() const
{
    return SimpleProtocol::MESSAGE;
}

bool
SimpleMessage::hasSequenceId() const
{
    return _hasSeqId;
}

uint64_t
SimpleMessage::getSequenceId() const
{
    return _seqId;
}

uint32_t
SimpleMessage::getApproxSize() const
{
    return _value.size();
}

} // namespace mbus
