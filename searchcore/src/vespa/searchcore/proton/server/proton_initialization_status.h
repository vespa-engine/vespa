// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <mutex>

namespace proton {

/**
 * Class that tracks the initialization state of Proton and keeps timestamps of when a state was entered.
 *
 * Thread-safe.
 */
class ProtonInitializationStatus {
public:
    enum State {
        INITIALIZING,
        READY
    };
    static std::string stateToString(State state);
    using time_point = std::chrono::system_clock::time_point;

private:
    mutable std::mutex _mutex;
    time_point _start_time;
    time_point _end_time;

    State _state;

public:
    ProtonInitializationStatus();

    State getState() const;

    void startInitialization();
    void endInitialization();

    time_point getStartTime() const;
    time_point getEndTime() const;
};

}
