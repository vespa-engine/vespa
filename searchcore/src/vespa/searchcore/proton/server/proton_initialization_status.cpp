// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_initialization_status.h"

#include <mutex>

namespace proton {

std::string ProtonInitializationStatus::stateToString(State state) {
    switch (state) {
        case State::INITIALIZING:
            return "initializing";
        case State::FINISHED:
            return "finished";
    }

    return "initializing";
}

ProtonInitializationStatus::ProtonInitializationStatus()
    : _state(State::INITIALIZING) {
}

ProtonInitializationStatus::State ProtonInitializationStatus::getState() const {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    return _state;
}

void ProtonInitializationStatus::startInitialization() {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    _start_time = std::chrono::system_clock::now();
}

void ProtonInitializationStatus::endInitialization() {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    _end_time = std::chrono::system_clock::now();
    _state = State::FINISHED;
}

ProtonInitializationStatus::time_point ProtonInitializationStatus::getStartTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);
    return _start_time;
}

ProtonInitializationStatus::time_point ProtonInitializationStatus::getEndTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);
    return _end_time;
}

}