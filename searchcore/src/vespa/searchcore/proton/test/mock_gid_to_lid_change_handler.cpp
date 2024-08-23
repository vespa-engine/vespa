// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

void
MockGidToLidChangeHandler::addListener(std::unique_ptr<IGidToLidChangeListener> listener) {
    _adds.emplace_back(listener->getDocTypeName(), listener->getName());
    _listeners.push_back(std::move(listener));
}

void
MockGidToLidChangeHandler::removeListeners(const std::string &docTypeName, const std::set<std::string> &keepNames) {
    _removes.emplace_back(docTypeName, keepNames);
}

}
