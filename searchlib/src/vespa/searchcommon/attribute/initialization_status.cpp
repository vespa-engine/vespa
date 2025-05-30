// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "initialization_status.h"

#include <mutex>

namespace search::attribute {

std::string InitializationStatus::stateToString(State state) {
    switch (state) {
    case State::QUEUED:
        return "queued";
    case State::LOADING:
        return "loading";
    case State::REPROCESSING:
        return "reprocessing";
    case State::LOADED:
        return "loaded";
    }

    return "queued";
}

InitializationStatus::InitializationStatus() :
    _state(State::QUEUED),
    _reprocessing_percentage(0.0f)
{
}

void InitializationStatus::startLoading() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _state = State::LOADING;
    _start_time = std::chrono::system_clock::now();
}

void InitializationStatus::startReprocessing() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _state = State::REPROCESSING;
    _reprocessing_start_time = std::chrono::system_clock::now();
    _reprocessing_percentage = 0.0f;
}

void InitializationStatus::endReprocessing() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _state = State::LOADING;
    _reprocessing_end_time = std::chrono::system_clock::now();
    _reprocessing_percentage = 1.0f;
}

void InitializationStatus::endLoading() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _state = State::LOADED;
    _end_time = std::chrono::system_clock::now();
}

void InitializationStatus::setReprocessingPercentage(float percentage) {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _reprocessing_percentage = percentage;
}

InitializationStatus::State InitializationStatus::getState() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _state;
}

InitializationStatus::time_point InitializationStatus::getStartTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _start_time;
}

InitializationStatus::time_point InitializationStatus::getEndTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _end_time;
}

InitializationStatus::time_point InitializationStatus::getReprocessingStartTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _reprocessing_start_time;
}

InitializationStatus::time_point InitializationStatus::getReprocessingEndTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _reprocessing_end_time;
}

float InitializationStatus::getReprocessingPercentage() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _reprocessing_percentage;
}

}
