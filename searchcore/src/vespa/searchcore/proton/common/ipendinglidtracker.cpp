// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ipendinglidtracker.h"

namespace proton {

IPendingLidTracker::Token::Token()
    : _tracker(nullptr),
      _lid(0u)
{}
IPendingLidTracker::Token::Token(uint32_t lid, IPendingLidTracker &tracker)
    : _tracker(&tracker),
      _lid(lid)
{}

IPendingLidTracker::Token::~Token() {
    if (_tracker != nullptr) {
        _tracker->consume(_lid);
    }
}

void
ILidCommitState::waitComplete(uint32_t lid) const {
    waitState(State::COMPLETED, lid);
}
void
ILidCommitState::waitComplete(const LidList & lids) const {
    waitState(State::COMPLETED, lids);
}

}
