// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "service.h"
#include <cassert>
#include <unistd.h>

#include <vespa/log/log.h>
#include <vespa/log/control-file.h>
LOG_SETUP("logdemon");


namespace logdemon {

unsigned long Component::defFwd = (unsigned long)-1;

Component::Component(const std::string & servicename, const std::string & name)
    : _isforwarding(defFwd), _lastseen(0.0), _lastpid(0),
      _myservice(servicename), _myname(name),
      _logctlname(name.substr(std::min(name.size(), name.find('.'))))
{
    assert(ns_log::Logger::NUM_LOGLEVELS < 32);
}

Component::~Component() = default;

void
Component::doLogAtAll(LogLevel level)
{
    using ns_log::ControlFile;

    char lcfn[FILENAME_MAX];
    if (! ControlFile::makeName(_myservice.c_str(), lcfn, FILENAME_MAX)) {
        LOG(debug, "no logcontrol file for service '%s'", _myservice.c_str());
        return;
    }
    try {
        ControlFile foo(lcfn, ControlFile::READWRITE);
        unsigned int *lstring = foo.getLevels(_logctlname.c_str());
        lstring[level] = CHARS_TO_UINT(' ', ' ', 'O', 'N');
    } catch (...) {
        LOG(debug, "exception changing logcontrol for %s", _myservice.c_str());
    }
}

void
Component::dontLogAtAll(LogLevel level)
{
    using ns_log::ControlFile;

    char lcfn[FILENAME_MAX];
    if (! ControlFile::makeName(_myservice.c_str(), lcfn, FILENAME_MAX)) {
        LOG(debug, "no logcontrol file for service '%s'", _myservice.c_str());
        return;
    }
    try {
        ControlFile foo(lcfn, ControlFile::READWRITE);
        unsigned int *lstring = foo.getLevels(_logctlname.c_str());
        lstring[level] = CHARS_TO_UINT(' ', 'O', 'F', 'F');
    } catch (...) {
        LOG(debug, "exception changing logcontrol for %s", _myservice.c_str());
    }
}

bool
Component::shouldLogAtAll(LogLevel level) const
{
    using ns_log::ControlFile;

    char lcfn[FILENAME_MAX];
    if (! ControlFile::makeName(_myservice.c_str(), lcfn, FILENAME_MAX)) {
        LOG(spam, "no logcontrol file for service '%s'", _myservice.c_str());
        return true;
    }
    try {
        ControlFile foo(lcfn, ControlFile::READWRITE);
        unsigned int *lstring = foo.getLevels(_logctlname.c_str());
        return (lstring[level] == CHARS_TO_UINT(' ', ' ', 'O', 'N'));
    } catch (...) {
        LOG(debug, "exception checking logcontrol for %s", _myservice.c_str());
    }
    return true;
}

Service::Service(const std::string & name)
    : _myname(name),
      _components()
{
}

Service::~Service() = default;

Component *
Service::getComponent(const std::string & comp) {
    auto found = _components.find(comp);
    if (found == _components.end()) {
        _components[comp] = std::make_unique<Component>(_myname, comp);
        found = _components.find(comp);
    }
    return found->second.get();
}

Service *
Services::getService(const std::string & serv) {
    auto found = _services.find(serv);
    if (found == _services.end()) {
        _services[serv] = std::make_unique<Service>(serv);
        found = _services.find(serv);
    }
    return found->second.get();
}

Services::Services() = default;
Services::~Services() = default;

void
Services::dumpState(int fildesc)
{
    using ns_log::Logger;

    for (const auto & serviceEntry : _services) {
        const Service & svc = *serviceEntry.second;
        const char * service = serviceEntry.first.c_str();
        for (const auto & entry : svc.components()) {
            const Component & cmp = *entry.second;
            const char * key = entry.first.c_str();
            char buf[1024];
            int pos = snprintf(buf, 1024, "setstate %s %s ", service, key);
            for (int i = 0; pos < 1000 && i < Logger::NUM_LOGLEVELS; i++) {
                LogLevel l = static_cast<LogLevel>(i);
                pos += snprintf(buf+pos, 1024-pos, "%s=%s,", Logger::logLevelNames[i],
                                cmp.shouldForward(l) ? "forward" : "store");
            }
            if (pos < 1000) {
                buf[pos-1]='\n';
                ssize_t writeRes = write(fildesc, buf, pos);
                if (writeRes != pos) {
                    LOG(warning, "Write failed, res=%zd, should be %d: %s",
                        writeRes, pos, strerror(errno));
                }
            } else {
                LOG(warning, "buffer to small to dumpstate[%s, %s]", service, key);
            }
        }
    }
}

}
