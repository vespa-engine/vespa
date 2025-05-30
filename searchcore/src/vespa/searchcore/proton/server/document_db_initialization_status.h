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
        READY
    };
    static std::string stateToString(State state);

public:
    using time_point = std::chrono::system_clock::time_point;

private:
    mutable std::shared_mutex _mutex;
    State _state;
    time_point _startTime;

public:
    DocumentDBInitializationStatus();
    State getState() const;
    void startInitialization();
    void startReplay();
    void finishInitialization();
    time_point getStartTime() const;
};

}
