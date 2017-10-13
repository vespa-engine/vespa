// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedtoken.h"

namespace proton {

FeedToken::FeedToken() = default;

FeedToken::FeedToken(ITransport &transport) :
    _state(new State(transport))
{
}

FeedToken::State::State(ITransport & transport) :
    _transport(transport),
    _result(new storage::spi::Result()),
    _documentWasFound(false),
    _alreadySent(false)
{
}

FeedToken::State::~State()
{
    ack();
}

void
FeedToken::State::ack()
{
    bool alreadySent = _alreadySent.exchange(true);
    if ( !alreadySent ) {
        _transport.send(std::move(_result), _documentWasFound);
    }
}

void
FeedToken::State::fail()
{
    bool alreadySent = _alreadySent.exchange(true);
    if ( !alreadySent ) {
        _transport.send(std::move(_result), _documentWasFound);
    }
}

} // namespace proton
