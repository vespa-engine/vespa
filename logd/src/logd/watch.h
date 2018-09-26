// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace logdemon {

class Forwarder;
class ConfSub;

class Watcher
{
private:
    char *_buffer;
    ConfSub&   _confsubscriber;
    Forwarder& _forwarder;
    int _wfd;
public:
    Watcher(const Watcher& other) = delete;
    Watcher& operator=(const Watcher& other) = delete;
    Watcher(ConfSub &cfs, Forwarder &fw);
    ~Watcher();

    void watchfile();
    void removeOldLogs(const char *prefix);
};

}
