// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedtoken.h"
#include <vespa/persistence/spi/result.h>

namespace proton::feedtoken {

State::State(ITransport & transport) :
    _transport(transport),
    _result(std::make_unique<storage::spi::Result>()),
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
State::setResult(ResultUP result, bool documentWasFound) {
    _documentWasFound = documentWasFound;
    _result = std::move(result);
}

bool
State::is_replay() const noexcept
{
    return false;
}

void
State::fail()
{
    bool alreadySent = _alreadySent.exchange(true);
    if ( !alreadySent ) {
        _transport.send(std::move(_result), _documentWasFound);
    }
}

OwningState::~OwningState() {
    ack();
}

} // namespace proton
