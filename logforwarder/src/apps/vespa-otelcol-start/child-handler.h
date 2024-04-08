// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "cf-handler.h"
#include <string>

class ChildHandler {
private:
    bool _childRunning = false;
    bool _terminating = false;
    int _childPid = 0;
public:
    ChildHandler();
    ~ChildHandler();
    void startChild(const std::string &progPath, const std::string &cfFile);
    void stopChild();
    bool checkChild();
};
