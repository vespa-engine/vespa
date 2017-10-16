// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedtoken.h"

namespace proton::feedtoken {

State::State(ITransport & transport) :
    _transport(transport),
    _result(new storage::spi::Result()),
    _documentWasFound(false),
    _alreadySent(false)
{
}

State::~State()
{
    ack();
}

void
State::ack()
{
    bool alreadySent = _alreadySent.exchange(true);
    if ( !alreadySent ) {
        _transport.send(std::move(_result), _documentWasFound);
    }
}

void
State::fail()
{
    bool alreadySent = _alreadySent.exchange(true);
    if ( !alreadySent ) {
        _transport.send(std::move(_result), _documentWasFound);
    }
}

} // namespace proton
