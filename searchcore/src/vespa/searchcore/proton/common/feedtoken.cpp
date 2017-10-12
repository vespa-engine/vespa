// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedtoken.h"

namespace proton {

FeedToken::FeedToken(ITransport &transport) :
    _state(new State(transport, 1))
{
}

FeedToken::State::State(ITransport & transport, uint32_t numAcksRequired) :
    _transport(transport),
    _result(new storage::spi::Result()),
    _documentWasFound(false),
    _unAckedCount(numAcksRequired)
{
    assert(_unAckedCount > 0);
}

FeedToken::State::~State()
{
    assert(_unAckedCount == 0);
}

void
FeedToken::State::ack()
{
    uint32_t prev(_unAckedCount--);
    if (prev == 1) {
        _transport.send(std::move(_result), _documentWasFound);
    }
    assert(prev >= 1);
}

void
FeedToken::State::incNeededAcks()
{
    uint32_t prev(_unAckedCount++);
    assert(prev >= 1);
    (void) prev;
}

void
FeedToken::State::fail()
{
    uint32_t prev = _unAckedCount.exchange(0);
    if (prev > 0) {
        _transport.send(std::move(_result), _documentWasFound);
    }
}

} // namespace proton
