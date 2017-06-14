// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <time.h>
#include <sys/stat.h>

#include <vespa/log/log.h>
LOG_SETUP("");
LOG_RCSID("$Id$");

#include "conn.h"
#include "service.h"
#include "forward.h"
#include "conf.h"
#include <vespa/config/common/exceptions.h>

using cloud::config::log::LogdConfig;
using ns_log::Logger;

namespace logdemon {

void
ConfSub::configure(std::unique_ptr<LogdConfig> cfg)
{
    const LogdConfig &newconf(*cfg);
    if (newconf.logserver.host != _logServer)
    {
        if (newconf.logserver.host.size() > 255) {
            LOG(warning, "too long logserver hostname: %s",
            newconf.logserver.host.c_str());
            return;
        }
        strcpy(_logServer, newconf.logserver.host.c_str());
        _newConf = true;
    }
    if (newconf.logserver.use != _use_logserver)
    {
        _use_logserver = newconf.logserver.use;
        _newConf = true;
    }
    ForwardMap forwardMap;
    forwardMap[Logger::fatal] = newconf.loglevel.fatal.forward;
    forwardMap[Logger::error] = newconf.loglevel.error.forward;
    forwardMap[Logger::warning] = newconf.loglevel.warning.forward;
    forwardMap[Logger::config] = newconf.loglevel.config.forward;
    forwardMap[Logger::info] = newconf.loglevel.info.forward;
    forwardMap[Logger::event] = newconf.loglevel.event.forward;
    forwardMap[Logger::debug] = newconf.loglevel.debug.forward;
    forwardMap[Logger::spam] = newconf.loglevel.spam.forward;
    _fw.setForwardMap(forwardMap);

    if (newconf.logserver.port != _logPort) {
        _logPort = newconf.logserver.port;
        _newConf = true;
    }
    if (newconf.rotate.size > 0) {
        _rotate_size = newconf.rotate.size;
    } else {
        LOG(config, "bad rotate.size=%d must be positive",
            newconf.rotate.size);
    }
    if (newconf.rotate.age > 0) {
        _rotate_age = newconf.rotate.age;
    } else {
        LOG(config, "bad rotate.age=%d must be positive",
            newconf.rotate.age);
    }
    if (newconf.remove.totalmegabytes > 0) {
        _remove_meg = newconf.remove.totalmegabytes;
    } else {
        LOG(config, "bad remove.totalmegabytes=%d must be positive",
            newconf.remove.totalmegabytes);
    }
    if (newconf.remove.age > 0) {
        _remove_age = newconf.remove.age;
    } else {
        LOG(config, "bad remove.age=%d must be positive",
            newconf.remove.age);
    }
}

void
ConfSub::latch()
{
    if (_subscriber.nextConfig(0))
        configure(_handle->getConfig());
    if (_newConf) {
        if (_use_logserver) {
            connectToLogserver();
        } else {
            connectToDevNull();
        }
    }
}

void
ConfSub::connectToLogserver()
{
    int newfd = makeconn(_logServer, _logPort);
    if (newfd >= 0) {
        resetFileDescriptor(newfd);
        LOG(debug, "connected to logserver at %s:%d",
            _logServer, _logPort);
    } else {
        LOG(debug, "could not connect to %s:%d",
            _logServer, _logPort);
    }
}

void
ConfSub::connectToDevNull()
{
    int newfd = open("/dev/null", O_RDWR);
    if (newfd >= 0) {
        resetFileDescriptor(newfd);
        LOG(debug, "opened /dev/null for read/write");
    } else {
        LOG(debug, "error opening /dev/null (%d): %s", newfd, strerror(newfd));
    }
}

void
ConfSub::resetFileDescriptor(int newfd)
{
    if (_logserverfd >= 0) {
        close(_logserverfd);
    }
    _logserverfd = newfd;
    _fw.setLogserverFD(newfd);
    _newConf = false;
}

void
ConfSub::closeConn()
{
    close(_logserverfd);
    _logserverfd = -1;
    _newConf = true;
}

ConfSub::ConfSub(Forwarder &fw, const config::ConfigUri & configUri)
    : _logPort(0),
      _logserverfd(-1),
      _rotate_size(INT_MAX),
      _rotate_age(INT_MAX),
      _remove_meg(INT_MAX),
      _remove_age(3650),
      _use_logserver(true),
      _fw(fw),
      _subscriber(configUri.getContext()),
      _handle(),
      _newConf(false)
{
    _logServer[0] = '\0';
    _handle = _subscriber.subscribe<LogdConfig>(configUri.getConfigId());
    _subscriber.nextConfig(0);
    configure(_handle->getConfig());

    LOG(debug, "got logServer %s", _logServer);
    LOG(debug, "got handle %p", _handle.get());
}

ConfSub::~ConfSub()
{
    LOG(debug, "forget logServer %s", _logServer);
    LOG(debug, "done ~ConfSub()");
}

} // namespace
