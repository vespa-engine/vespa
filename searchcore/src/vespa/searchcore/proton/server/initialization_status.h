// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <shared_mutex>

namespace proton {

class InitializationStatus {
public:
    using time_point = std::chrono::system_clock::time_point;

private:
    mutable std::shared_mutex _mutex;
    time_point _startTime;

public:
    void startInitialization();
    time_point getStartTime() const;
};

}
