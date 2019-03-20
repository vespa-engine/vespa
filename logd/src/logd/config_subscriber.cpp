// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config_subscriber.h"
#include "conn.h"
#include "forwarder.h"
#include <fcntl.h>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("");

using cloud::config::log::LogdConfig;
using ns_log::Logger;

namespace logdemon {

void
ConfigSubscriber::configure(std::unique_ptr<LogdConfig> cfg)
{
    const LogdConfig &newconf(*cfg);
    if (newconf.logserver.host != _logServer) {
        _logServer = newconf.logserver.host;
        _needToConnect = true;
    }
    if (newconf.logserver.use != _use_logserver) {
        _use_logserver = newconf.logserver.use;
        _needToConnect = true;
    }
    _statePort = newconf.stateport;

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
        _needToConnect = true;
    }
    if (newconf.rotate.size > 0) {
        _rotate_size = newconf.rotate.size;
    } else {
        LOG(config, "bad rotate.size=%d must be positive", newconf.rotate.size);
    }
    if (newconf.rotate.age > 0) {
        _rotate_age = newconf.rotate.age;
    } else {
        LOG(config, "bad rotate.age=%d must be positive", newconf.rotate.age);
    }
    if (newconf.remove.totalmegabytes > 0) {
        _remove_meg = newconf.remove.totalmegabytes;
    } else {
        LOG(config, "bad remove.totalmegabytes=%d must be positive", newconf.remove.totalmegabytes);
    }
    if (newconf.remove.age > 0) {
        _remove_age = newconf.remove.age;
    } else {
        LOG(config, "bad remove.age=%d must be positive", newconf.remove.age);
    }
}

bool
ConfigSubscriber::checkAvailable()
{
    if (_subscriber.nextGeneration(0)) {
        _hasAvailable = true;
    }
    return _hasAvailable;
}

void
ConfigSubscriber::latch()
{
    if (checkAvailable()) {
        configure(_handle->getConfig());
        _hasAvailable = false;
    }
    if (_needToConnect) {
        if (_use_logserver) {
            connectToLogserver();
        } else {
            connectToDevNull();
        }
    }
}

void
ConfigSubscriber::connectToLogserver()
{
    int newfd = makeconn(_logServer.c_str(), _logPort);
    if (newfd >= 0) {
        resetFileDescriptor(newfd);
        LOG(debug, "connected to logserver at %s:%d", _logServer.c_str(), _logPort);
    } else {
        LOG(debug, "could not connect to %s:%d", _logServer.c_str(), _logPort);
    }
}

void
ConfigSubscriber::connectToDevNull()
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
ConfigSubscriber::resetFileDescriptor(int newfd)
{
    if (_logserverfd >= 0) {
        close(_logserverfd);
    }
    _logserverfd = newfd;
    _fw.setLogserverFD(newfd);
    _needToConnect = false;
}

void
ConfigSubscriber::closeConn()
{
    close(_logserverfd);
    _logserverfd = -1;
    _needToConnect = true;
}

ConfigSubscriber::ConfigSubscriber(Forwarder &fw, const config::ConfigUri & configUri)
    : _logServer(),
      _logPort(0),
      _logserverfd(-1),
      _statePort(0),
      _rotate_size(INT_MAX),
      _rotate_age(INT_MAX),
      _remove_meg(INT_MAX),
      _remove_age(3650),
      _use_logserver(true),
      _fw(fw),
      _subscriber(configUri.getContext()),
      _handle(),
      _hasAvailable(false),
      _needToConnect(true)
{
    _handle = _subscriber.subscribe<LogdConfig>(configUri.getConfigId());
    _subscriber.nextConfig(0);
    configure(_handle->getConfig());

    LOG(debug, "got logServer %s", _logServer.c_str());
    LOG(debug, "got handle %p", _handle.get());
}

ConfigSubscriber::~ConfigSubscriber()
{
    LOG(debug, "forget logServer %s", _logServer.c_str());
    LOG(debug, "done ~ConfSub()");
}

}
