// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton_initialization_status.h"

#include "ddbstate.h"
#include "document_db_initialization_status.h"

namespace proton {

std::string ProtonInitializationStatus::state_to_string(State state) {
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

void ProtonInitializationStatus::addDocumentDBInitializationStatus(const std::shared_ptr<DocumentDBInitializationStatus>& status) {
    _ddb_initialization_statuses.push_back(status);
}

void ProtonInitializationStatus::removeDocumentDBInitializationStatus(const std::shared_ptr<DocumentDBInitializationStatus>& status) {
    _ddb_initialization_statuses.erase(std::remove(_ddb_initialization_statuses.begin(), _ddb_initialization_statuses.end(), status),
                                   _ddb_initialization_statuses.end());
}

ProtonInitializationStatus::State ProtonInitializationStatus::get_state() const {
    std::lock_guard<std::mutex> guard(_mutex);
    return _state;
}

void ProtonInitializationStatus::start_initialization() {
    std::lock_guard<std::mutex> guard(_mutex);
    _start_time = std::chrono::system_clock::now();
}

void ProtonInitializationStatus::end_initialization() {
    std::lock_guard<std::mutex> guard(_mutex);
    _end_time = std::chrono::system_clock::now();
    _state = State::READY;
}

ProtonInitializationStatus::time_point ProtonInitializationStatus::get_start_time() const {
    std::lock_guard<std::mutex> guard(_mutex);
    return _start_time;
}

ProtonInitializationStatus::time_point ProtonInitializationStatus::get_end_time() const {
    std::lock_guard<std::mutex> guard(_mutex);
    return _end_time;
}

}
