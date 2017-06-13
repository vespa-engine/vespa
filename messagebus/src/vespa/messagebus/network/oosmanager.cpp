// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "oosmanager.h"
#include "rpcnetwork.h"
#include <algorithm>
#include <vespa/fnet/frt/frt.h>

namespace mbus {

OOSClient::SP
OOSManager::getClient(const string &spec)
{
    for (uint32_t i = 0; i < _clients.size(); ++i) {
        if (_clients[i]->getSpec() == spec) {
            return _clients[i];
        }
    }
    return OOSClient::SP(new OOSClient(_orb, spec));
}

void
OOSManager::PerformTask()
{
    bool changed = false;
    if (_slobrokGen != _mirror.updates()) {
        _slobrokGen = _mirror.updates();
        SpecList newServices = _mirror.lookup(_servicePattern);
        std::sort(newServices.begin(), newServices.end());
        if (newServices != _services) {
            ClientList newClients;
            for (uint32_t i = 0; i < newServices.size(); ++i) {
                newClients.push_back(getClient(newServices[i].second));
            }
            _services.swap(newServices);
            _clients.swap(newClients);
            changed = true;
        }
    }
    bool allOk = _mirror.ready();
    for (uint32_t i = 0; i < _clients.size(); ++i) {
        if (_clients[i]->isChanged()) {
            changed = true;
        }
        if (!_clients[i]->isReady()) {
            allOk = false;
        }
    }
    if (changed) {
        OOSSet oos(new StringSet());
        for (uint32_t i = 0; i < _clients.size(); ++i) {
            _clients[i]->dumpState(*oos);
        }
        vespalib::LockGuard guard(_lock);
        _oosSet.swap(oos);
    }
    if (allOk && !_ready) {
        _ready = true;
    }
    Schedule(_ready ? 1.0 : 0.1);
}

OOSManager::OOSManager(FRT_Supervisor &orb,
                       IMirrorAPI &mirror,
                       const string &servicePattern)
    : FNET_Task(orb.GetScheduler()),
      _orb(orb),
      _mirror(mirror),
      _disabled(servicePattern.empty()),
      _ready(_disabled),
      _lock("mbus::OOSManager::_lock", false),
      _servicePattern(servicePattern),
      _slobrokGen(0),
      _clients(),
      _oosSet()
{
    if (!_disabled) {
        ScheduleNow();
    }
}

OOSManager::~OOSManager()
{
    Kill();
}

bool
OOSManager::isOOS(const string &service)
{
    if (_disabled) {
        return false;
    }
    vespalib::LockGuard guard(_lock);
    if (_oosSet.get() == NULL) {
        return false;
    }
    if (_oosSet->find(service) == _oosSet->end()) {
        return false;
    }
    return true;
}

} // namespace mbus
