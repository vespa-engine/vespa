// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <logd/config-logd.h>
#include <vespa/log/log.h>
#include <unordered_map>

namespace logdemon {

typedef ns_log::Logger::LogLevel LogLevel;

class Component
{
    unsigned long    _isforwarding;
    double           _lastseen;
    int              _lastpid;
    const char      *_myservice;
    vespalib::string _myname;
    vespalib::string _logctlname;

    static unsigned long defFwd;
public:
    static void defaultDoForward(LogLevel level)   { defFwd |=  (1 << level); }
    static void defaultDontForward(LogLevel level) { defFwd &= ~(1 << level); }

    void doForward(LogLevel level)   { _isforwarding |=  (1 << level); }
    void dontForward(LogLevel level) { _isforwarding &= ~(1 << level); }
    bool shouldForward(LogLevel level) const {
        return ((_isforwarding & (1 << level)) != 0);
    }
    void doLogAtAll(LogLevel level);
    void dontLogAtAll(LogLevel level);
    bool shouldLogAtAll(LogLevel level) const;
    Component(const char *servicename, const char *name);
    ~Component();
    void remember(double t, int p) { _lastseen = t; _lastpid = p; }
    double lastSeen() const { return _lastseen; }
    double lastPid() const  { return _lastpid; }
};

class Service
{
private:
    vespalib::string _myname;
    Service(const Service& other);
    Service& operator= (const Service& other);
public:
    std::unordered_map<std::string, std::unique_ptr<Component>> _components;
    Component *getComponent(const vespalib::string & comp);
    Service(const char *name);
    ~Service();
};

class Services
{
public:
    std::unordered_map<std::string, std::unique_ptr<Service>> _services;
    Service *getService(const vespalib::string & serv);
    Services() : _services() {}
    ~Services();
    void dumpState(int fildesc);
};

} // namespace

