// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplereply.h"
#include "simpleprotocol.h"

namespace mbus {

SimpleReply::SimpleReply(const string &str) :
    Reply(),
    _value(str)
{ }

SimpleReply::~SimpleReply() { }

void
SimpleReply::setValue(const string &value)
{
    _value = value;
}

const string &
SimpleReply::getValue() const
{
    return _value;
}

const string &
SimpleReply::getProtocol() const
{
    return SimpleProtocol::NAME;
}

uint32_t
SimpleReply::getType() const
{
    return SimpleProtocol::REPLY;
}

} // namespace mbus
