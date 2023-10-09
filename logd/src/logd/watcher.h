// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>

namespace logdemon {

class Forwarder;
class ConfigSubscriber;

/**
 * Class used to watch a log file and forward new log lines to the logserver.
 */
class Watcher
{
private:
    std::vector<char>  _buffer;
    ConfigSubscriber & _confsubscriber;
    Forwarder        & _forwarder;
    int                _wfd;
    char * getBuf() { return &_buffer[0]; }
    long getBufSize() const { return _buffer.size(); }
public:
    Watcher(const Watcher& other) = delete;
    Watcher& operator=(const Watcher& other) = delete;
    Watcher(ConfigSubscriber &cfs, Forwarder &fw);
    ~Watcher();

    void watchfile();
    void removeOldLogs(const char *prefix);
};

}
