// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_initialization_status.h"

namespace proton {

std::string ProtonInitializationStatus::stateToString(State state) {
    switch (state) {
        case State::INITIALIZING:
            return "initializing";
        case State::READY:
            return "ready";
    }

    return "initializing";
}

ProtonInitializationStatus::ProtonInitializationStatus()
    : _state(State::INITIALIZING) {
}

ProtonInitializationStatus::State ProtonInitializationStatus::getState() const {
    std::lock_guard<std::mutex> guard(_mutex);
    return _state;
}

void ProtonInitializationStatus::startInitialization() {
    std::lock_guard<std::mutex> guard(_mutex);
    _start_time = std::chrono::system_clock::now();
}

void ProtonInitializationStatus::endInitialization() {
    std::lock_guard<std::mutex> guard(_mutex);
    _end_time = std::chrono::system_clock::now();
    _state = State::READY;
}

ProtonInitializationStatus::time_point ProtonInitializationStatus::getStartTime() const {
    std::lock_guard<std::mutex> guard(_mutex);
    return _start_time;
}

ProtonInitializationStatus::time_point ProtonInitializationStatus::getEndTime() const {
    std::lock_guard<std::mutex> guard(_mutex);
    return _end_time;
}

}