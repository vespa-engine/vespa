// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initialization_status.h"

namespace search::attribute {

std::string AttributeInitializationStatus::state_to_string(State state) {
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
    _was_reprocessed(false),
    _reprocessing_percentage(0.0f)
{
}

void AttributeInitializationStatus::start_loading() {
    std::lock_guard<std::mutex> guard(_mutex);

    _state = State::LOADING;
    _start_time = std::chrono::system_clock::now();
}

void AttributeInitializationStatus::start_reprocessing() {
    std::lock_guard<std::mutex> guard(_mutex);

    _state = State::REPROCESSING;
    _reprocessing_start_time = std::chrono::system_clock::now();
    _was_reprocessed = true;
    _reprocessing_percentage = 0.0f;
}

void AttributeInitializationStatus::end_reprocessing() {
    std::lock_guard<std::mutex> guard(_mutex);

    _state = State::LOADING;
    _reprocessing_end_time = std::chrono::system_clock::now();
    _reprocessing_percentage = 1.0f;
}

void AttributeInitializationStatus::end_loading() {
    std::lock_guard<std::mutex> guard(_mutex);

    _state = State::LOADED;
    _end_time = std::chrono::system_clock::now();
}

void AttributeInitializationStatus::set_reprocessing_percentage(float percentage) {
    std::lock_guard<std::mutex> guard(_mutex);

    _reprocessing_percentage = percentage;
}

AttributeInitializationStatus::State AttributeInitializationStatus::get_state() const {
    std::lock_guard<std::mutex> guard(_mutex);

    return _state;
}

AttributeInitializationStatus::time_point AttributeInitializationStatus::get_start_time() const {
    std::lock_guard<std::mutex> guard(_mutex);

    return _start_time;
}

AttributeInitializationStatus::time_point AttributeInitializationStatus::get_end_time() const {
    std::lock_guard<std::mutex> guard(_mutex);

    return _end_time;
}

AttributeInitializationStatus::time_point AttributeInitializationStatus::get_reprocessing_start_time() const {
    std::lock_guard<std::mutex> guard(_mutex);

    return _reprocessing_start_time;
}

AttributeInitializationStatus::time_point AttributeInitializationStatus::get_reprocessing_end_time() const {
    std::lock_guard<std::mutex> guard(_mutex);

    return _reprocessing_end_time;
}

bool AttributeInitializationStatus::was_reprocessed() const {
    std::lock_guard<std::mutex> guard(_mutex);
    return _was_reprocessed;
}

float AttributeInitializationStatus::get_reprocessing_percentage() const {
    std::lock_guard<std::mutex> guard(_mutex);

    return _reprocessing_percentage;
}

void AttributeInitializationStatus::report_initialization_status(const vespalib::slime::Inserter &inserter) const {
    std::lock_guard<std::mutex> guard(_mutex);

    vespalib::slime::Cursor &cursor = inserter.insertObject();
    cursor.setString("name", _name);

    cursor.setString("status", state_to_string(_state));

    if (_state >= State::REPROCESSING && _was_reprocessed) {
        cursor.setString("reprocessing_progress",  std::format("{:.6f}", _reprocessing_percentage));
    }

    if (_state > State::QUEUED) {
        cursor.setString("loading_started", timepoint_to_string(_start_time));
    }

    if (_state >= State::REPROCESSING && _was_reprocessed) {
        cursor.setString("reprocessing_started",timepoint_to_string(_reprocessing_start_time));
    }

    if (_state >= State::REPROCESSING_FINISHED && _was_reprocessed) {
        cursor.setString("reprocessing_finished", timepoint_to_string(_reprocessing_end_time));
    }

    if (_state == State::LOADED) {
        cursor.setString("loading_finished", timepoint_to_string(_end_time));
    }
}

}
