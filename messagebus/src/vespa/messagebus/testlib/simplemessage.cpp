// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplemessage.h"
#include "simpleprotocol.h"
#include <vespa/messagebus/metadata_extractor.h>
#include <vespa/messagebus/metadata_injector.h>

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

bool SimpleMessage::hasMetadata() const noexcept {
    return _foo_meta || _bar_meta;
}

void SimpleMessage::injectMetadata(MetadataInjector& injector) const {
    if (_foo_meta) {
        injector.inject_key_value("foo", *_foo_meta);
    }
    if (_bar_meta) {
        injector.inject_key_value("bar", *_bar_meta);
    }
}

void SimpleMessage::extractMetadata(const MetadataExtractor& extractor) {
    if (auto v = extractor.extract_value("foo")) {
        _foo_meta = v;
    }
    if (auto v = extractor.extract_value("bar")) {
        _bar_meta = v;
    }
}

} // namespace mbus
