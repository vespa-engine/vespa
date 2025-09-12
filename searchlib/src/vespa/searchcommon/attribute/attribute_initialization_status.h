// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/slime/slime.h>

#include <chrono>
#include <mutex>

namespace search::attribute {

/**
 * Class that tracks the initialization state of an attribute and keeps timestamps of when a state was entered.
 *
 * Thread-safe.
 */
class AttributeInitializationStatus {
public:
    enum State {
        QUEUED,
        LOADING,
        REPROCESSING,
        REPROCESSING_FINISHED,
        LOADED
    };
    static std::string state_to_string(State state);
    using time_point = std::chrono::system_clock::time_point;

    AttributeInitializationStatus(const std::string &name);

    void start_loading();
    void start_reprocessing();
    void end_reprocessing();
    void end_loading();
    void set_reprocessing_percentage(float percentage);

    const std::string& get_name() const { return _name; }
    State get_state() const;
    time_point get_start_time() const;
    time_point get_end_time() const;
    time_point get_reprocessing_start_time() const;
    time_point get_reprocessing_end_time() const;
    bool was_reprocessed() const;
    float get_reprocessing_percentage() const;

    void report_initialization_status(const vespalib::slime::Inserter &inserter) const;

private:
    mutable std::mutex _mutex;

    const std::string _name;
    State _state;

    time_point _start_time;
    time_point _reprocessing_start_time;
    time_point _reprocessing_end_time;
    time_point _end_time;

    bool _was_reprocessed;
    float _reprocessing_percentage;
};

}
