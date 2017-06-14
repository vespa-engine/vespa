// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <logd/config-logd.h>
#include <vespa/vespalib/util/hashmap.h>
#include <vespa/log/log.h>
#include <cassert>

namespace logdemon {

typedef ns_log::Logger::LogLevel LogLevel;

class Component
{
    unsigned long _isforwarding;
    double        _lastseen;
    int           _lastpid;
    const char   *_myservice;
    char         *_myname;
    char         *_logctlname;

    Component(const Component& other);
    Component& operator= (const Component& other);

    static unsigned long defFwd;
public:
    static void defaultDoForward(LogLevel level)   { defFwd |=  (1 << level); }
    static void defaultDontForward(LogLevel level) { defFwd &= ~(1 << level); }

    void doForward(LogLevel level)   { _isforwarding |=  (1 << level); }
    void dontForward(LogLevel level) { _isforwarding &= ~(1 << level); }
    bool shouldForward(LogLevel level) {
        return ((_isforwarding & (1 << level)) != 0);
    }
    void doLogAtAll(LogLevel level);
    void dontLogAtAll(LogLevel level);
    bool shouldLogAtAll(LogLevel level);
    Component(const char *servicename, const char *name)
        : _isforwarding(defFwd), _lastseen(0.0), _lastpid(0),
          _myservice(servicename), _myname(strdup(name)),
          _logctlname(strdup(name))
        {
            assert(ns_log::Logger::NUM_LOGLEVELS < 32);
            const char *withoutprefix = strchr(name, '.');
            if (withoutprefix != NULL) {
                strcpy(_logctlname, withoutprefix);
            } else {
                strcpy(_logctlname, "");
            }
        }
    ~Component() { free(_myname); free(_logctlname); }
    void remember(double t, int p) { _lastseen = t; _lastpid = p; }
    double lastSeen() const { return _lastseen; }
    double lastPid() const  { return _lastpid; }
};

typedef vespalib::HashMap<Component *> CompMap;
typedef vespalib::HashMap<Component *>::Iterator CompIter;

class Service
{
private:
    char *_myname;
    Service(const Service& other);
    Service& operator= (const Service& other);
public:
    CompMap _components;
    Component *getComponent(const char *comp) {
        if (! _components.isSet(comp)) {
            _components.set(comp, new Component(_myname, comp));
        }
        return _components[comp];
    }
    Service(const char *name)
        : _myname(strdup(name)), _components(NULL) {}
    ~Service();
};

typedef vespalib::HashMap<Service *> ServMap;
typedef vespalib::HashMap<Service *>::Iterator ServIter;

class Services
{
public:
    ServMap _services;
    Service *getService(const char *serv) {
        if (! _services.isSet(serv)) {
            _services.set(serv, new Service(serv));
        }
        return _services[serv];
    }
    Services() : _services(NULL) {}
    ~Services();
    void dumpState(int fildesc);
};

} // namespace

