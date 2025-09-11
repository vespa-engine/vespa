// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initialization_status.h"

#include <mutex>

namespace {
std::string timepointToString(search::attribute::AttributeInitializationStatus::time_point tp) {
    time_t secs = std::chrono::duration_cast<std::chrono::seconds>(tp.time_since_epoch()).count();
    uint32_t usecs_part = std::chrono::duration_cast<std::chrono::microseconds>(tp.time_since_epoch()).count() % 1000000;
    return std::format("{}.{:06}", secs, usecs_part);
}
}

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

AttributeInitializationStatus::AttributeInitializationStatus(const std::string &name) :
    _name(name),
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

void AttributeInitializationStatus::reportInitializationStatus(const vespalib::slime::Inserter &inserter) const {
vespalib::slime::Cursor &cursor = inserter.insertObject();
cursor.setString("name", getName());

cursor.setString("status", stateToString(getState()));

if (getState() >= State::REPROCESSING && didReprocess()) {
    cursor.setString("reprocessing_progress",  std::format("{:.6f}", getReprocessingPercentage()));
}

if (getState() > State::QUEUED) {
    cursor.setString("loading_started", timepointToString(getStartTime()));
}

if (getState() >= State::REPROCESSING && didReprocess()) {
    cursor.setString("reprocessing_started",timepointToString(getReprocessingStartTime()));
}

if (getState() >= State::REPROCESSING_FINISHED && didReprocess()) {
    cursor.setString("reprocessing_finished", timepointToString(getReprocessingEndTime()));
}

if (getState() == State::LOADED) {
    cursor.setString("loading_finished", timepointToString(getEndTime()));
}
}

}
