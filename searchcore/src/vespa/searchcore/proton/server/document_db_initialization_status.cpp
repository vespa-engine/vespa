// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_initialization_status.h"

#include <mutex>

namespace proton {

std::string DocumentDBInitializationStatus::stateToString(State state) {
        switch (state) {
        case State::LOAD:
            return "load";
        case State::REPLAYING:
            return "replaying";
        case State::READY:
            return "ready";
        }

        return "load";
    }

DocumentDBInitializationStatus::DocumentDBInitializationStatus() :
    _state(State::LOAD) {
}

DocumentDBInitializationStatus::State DocumentDBInitializationStatus::getState() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);
    return _state;
}

void DocumentDBInitializationStatus::startInitialization() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _start_time = std::chrono::system_clock::now();
}

DocumentDBInitializationStatus::time_point DocumentDBInitializationStatus::getStartTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _start_time;
}

DocumentDBInitializationStatus::time_point DocumentDBInitializationStatus::getEndTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _end_time;
}

DocumentDBInitializationStatus::time_point DocumentDBInitializationStatus::getReplayStartTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _replay_start_time;
}

void DocumentDBInitializationStatus::startReplay() {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    _state = State::REPLAYING;
    _replay_start_time = std::chrono::system_clock::now();
}

void DocumentDBInitializationStatus::finishInitialization() {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    _state = State::READY;
    _end_time = std::chrono::system_clock::now();
}

}
