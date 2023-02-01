// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_gid_to_lid_change_handler.h"

namespace proton::test {

MockGidToLidChangeHandler::MockGidToLidChangeHandler() noexcept
    : IGidToLidChangeHandler(),
      _adds(),
      _removes(),
      _listeners()
{
}

MockGidToLidChangeHandler::~MockGidToLidChangeHandler() = default;

}
