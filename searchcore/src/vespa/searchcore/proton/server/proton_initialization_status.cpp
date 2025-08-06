// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_initialization_status.h"

#include <mutex>

namespace proton {

void ProtonInitializationStatus::startInitialization() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _startTime = std::chrono::system_clock::now();
}

ProtonInitializationStatus::time_point ProtonInitializationStatus::getStartTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _startTime;
}

}