// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>

class ChildHandler {
private:
    bool _childRunning;
    std::string _runningPrefix;
public:
    void startChild(const std::string &prefix);
    void stopChild();
    void stopChild(const std::string &prefix);
    ChildHandler();
};
