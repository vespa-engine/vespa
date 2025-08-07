// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <shared_mutex>

namespace search::attribute {

class AttributeInitializationStatus {
public:
    enum State {
        QUEUED,
        LOADING,
        REPROCESSING,
        LOADED
    };
    static std::string stateToString(State state);
    using time_point = std::chrono::system_clock::time_point;

    AttributeInitializationStatus();

    void startLoading();
    void startReprocessing();
    void endReprocessing();
    void endLoading();
    void setReprocessingPercentage(float percentage);

    State getState() const;
    time_point getStartTime() const;
    time_point getEndTime() const;
    time_point getReprocessingStartTime() const;
    time_point getReprocessingEndTime() const;
    bool didReprocess() const;
    float getReprocessingPercentage() const;

private:
    mutable std::shared_mutex _mutex;

    State _state;

    time_point _start_time;
    time_point _reprocessing_start_time;
    time_point _reprocessing_end_time;
    time_point _end_time;

    bool _didReprocess;
    float _reprocessing_percentage;
};

}
