// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <shared_mutex>

namespace proton {

class DocumentDBInitializationStatus {
public:
    enum State {
        LOAD,
        REPLAYING,
        REPLAY_FINISHED,
        READY
    };
    static std::string stateToString(State state);

public:
    using time_point = std::chrono::system_clock::time_point;

private:
    mutable std::shared_mutex _mutex;
    State _state;
    time_point _start_time;
    time_point _end_time;
    time_point _replay_start_time;
    time_point _replay_end_time;

public:
    DocumentDBInitializationStatus();
    State getState() const;
    void startInitialization();
    void startReplay();
    void finishReplay();
    void finishInitialization();

    time_point getStartTime() const;
    time_point getEndTime() const;
    time_point getReplayStartTime() const;
    time_point getReplayEndTime() const;
};

}
