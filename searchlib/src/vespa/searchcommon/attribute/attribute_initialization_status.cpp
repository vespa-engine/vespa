// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initialization_status.h"

#include <mutex>

namespace search::attribute {

std::string AttributeInitializationStatus::stateToString(State state) {
    switch (state) {
    case State::QUEUED:
        return "queued";
    case State::LOADING:
        return "loading";
    case State::REPROCESSING:
        return "reprocessing";
    case State::REPROCESSING_FINISHED:
        return "reprocessing_finished";
    case State::LOADED:
        return "loaded";
    }

    return "queued";
}

AttributeInitializationStatus::AttributeInitializationStatus() :
    _state(State::QUEUED),
    _didReprocess(false),
    _reprocessing_percentage(0.0f)
{
}

void AttributeInitializationStatus::startLoading() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _state = State::LOADING;
    _start_time = std::chrono::system_clock::now();
}

void AttributeInitializationStatus::startReprocessing() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _state = State::REPROCESSING;
    _reprocessing_start_time = std::chrono::system_clock::now();
    _didReprocess = true;
    _reprocessing_percentage = 0.0f;
}

void AttributeInitializationStatus::endReprocessing() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _state = State::LOADING;
    _reprocessing_end_time = std::chrono::system_clock::now();
    _reprocessing_percentage = 1.0f;
}

void AttributeInitializationStatus::endLoading() {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _state = State::LOADED;
    _end_time = std::chrono::system_clock::now();
}

void AttributeInitializationStatus::setReprocessingPercentage(float percentage) {
    std::unique_lock<std::shared_mutex> guard(_mutex);

    _reprocessing_percentage = percentage;
}

AttributeInitializationStatus::State AttributeInitializationStatus::getState() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _state;
}

AttributeInitializationStatus::time_point AttributeInitializationStatus::getStartTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _start_time;
}

AttributeInitializationStatus::time_point AttributeInitializationStatus::getEndTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _end_time;
}

AttributeInitializationStatus::time_point AttributeInitializationStatus::getReprocessingStartTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _reprocessing_start_time;
}

AttributeInitializationStatus::time_point AttributeInitializationStatus::getReprocessingEndTime() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _reprocessing_end_time;
}

bool AttributeInitializationStatus::didReprocess() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);
    return _didReprocess;
}

float AttributeInitializationStatus::getReprocessingPercentage() const {
    std::shared_lock<std::shared_mutex> guard(_mutex);

    return _reprocessing_percentage;
}

}
