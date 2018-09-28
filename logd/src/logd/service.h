// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <logd/config-logd.h>
#include <vespa/log/log.h>
#include <unordered_map>

namespace logdemon {

typedef ns_log::Logger::LogLevel LogLevel;

class Component
{
    unsigned long  _isforwarding;
    double         _lastseen;
    int            _lastpid;
    std::string    _myservice;
    std::string    _myname;
    std::string    _logctlname;

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
    Component(const std::string & servicename, const std::string & name);
    ~Component();
    void remember(double t, int p) { _lastseen = t; _lastpid = p; }
    double lastSeen() const { return _lastseen; }
    double lastPid() const  { return _lastpid; }
};

class Service
{
public:
    using ComponentMap = std::unordered_map<std::string, std::unique_ptr<Component>>;
    Service(const Service& other) = delete;
    Service& operator= (const Service& other) = delete;
    Service(const std::string & name);
    ~Service();
    Component *getComponent(const std::string & comp);
    const ComponentMap & components() const { return _components; }
private:
    std::string  _myname;
    ComponentMap _components;
};

class Services
{
public:
    std::unordered_map<std::string, std::unique_ptr<Service>> _services;
    Service *getService(const std::string & serv);
    Services();
    ~Services();
    void dumpState(int fildesc);
};

} // namespace

