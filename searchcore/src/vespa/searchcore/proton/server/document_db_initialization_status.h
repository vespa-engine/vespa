// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <mutex>

namespace proton {

/**
 * Class that tracks the initialization state of a DocumentDB and keeps timestamps of when a state was entered.
 *
 * Thread-safe.
 */
class DocumentDBInitializationStatus {
public:
    enum State {
        LOAD,
        REPLAYING,
        READY
    };
    static std::string stateToString(State state);

public:
    using time_point = std::chrono::system_clock::time_point;

private:
    mutable std::mutex _mutex;
    State _state;
    time_point _start_time;
    time_point _end_time;
    time_point _replay_start_time;

public:
    DocumentDBInitializationStatus();
    State getState() const;
    void startInitialization();
    void startReplay();
    void finishInitialization();

    time_point getStartTime() const;
    time_point getEndTime() const;
    time_point getReplayStartTime() const;
};

}
