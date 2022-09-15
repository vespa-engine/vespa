// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

class ChildHandler {
private:
    bool _childRunning;
    vespalib::string _runningPrefix;
public:
    void startChild(const vespalib::string &prefix);
    void stopChild();
    void stopChild(const vespalib::string &prefix);
    ChildHandler();
};
