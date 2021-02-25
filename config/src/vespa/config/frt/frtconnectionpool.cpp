// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "frtconnectionpool.h"
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>

namespace config {

FRTConnectionPool::FRTConnectionKey::FRTConnectionKey(int idx, const vespalib::string& hostname)
    : _idx(idx),
      _hostname(hostname)
{
}

int
FRTConnectionPool::FRTConnectionKey::operator<(const FRTConnectionPool::FRTConnectionKey& right) const
{
    return _idx < right._idx;
}

int
FRTConnectionPool::FRTConnectionKey::operator==(const FRTConnectionKey& right) const
{
    return _hostname == right._hostname;
}

FRTConnectionPool::FRTConnectionPool(const ServerSpec & spec, const TimingValues & timingValues)
    :  _threadPool(std::make_unique<FastOS_ThreadPool>(60_Ki)),
       _transport(std::make_unique<FNET_Transport>()),
       _supervisor(std::make_unique<FRT_Supervisor>(_transport.get())),
      _selectIdx(0),
      _hostname("")
{
    for (size_t i(0); i < spec.numHosts(); i++) {
        FRTConnectionKey key(i, spec.getHost(i));
        _connections[key] = std::make_shared<FRTConnection>(spec.getHost(i), *_supervisor, timingValues);
    }
    setHostname();
    _transport->Start(_threadPool.get());
}

FRTConnectionPool::~FRTConnectionPool()
{
    _transport->ShutDown(true);
}

void
FRTConnectionPool::syncTransport()
{
    _supervisor->GetTransport()->sync();
}

Connection *
FRTConnectionPool::getCurrent()
{
    if (_hostname.compare("") == 0) {
        return getNextRoundRobin();
    } else {
        return getNextHashBased();
    }
}

FRTConnection *
FRTConnectionPool::getNextRoundRobin()
{
    std::vector<FRTConnection *> readySources;
    getReadySources(readySources);
    std::vector<FRTConnection *> suspendedSources;
    getSuspendedSources(suspendedSources);
    FRTConnection* nextFRTConnection = nullptr;

    if (!readySources.empty()) {
        int sel = _selectIdx % (int)readySources.size();
        _selectIdx = sel + 1;
        nextFRTConnection = readySources[sel];
    } else if (!suspendedSources.empty()) {
        int sel = _selectIdx % (int)suspendedSources.size();
        _selectIdx = sel + 1;
        nextFRTConnection = suspendedSources[sel];
    }
    return nextFRTConnection;
}

FRTConnection *
FRTConnectionPool::getNextHashBased()
{
    std::vector<FRTConnection*> readySources;
    getReadySources(readySources);
    std::vector<FRTConnection*> suspendedSources;
    getSuspendedSources(suspendedSources);
    FRTConnection* nextFRTConnection = nullptr;

    if (!readySources.empty()) {
        int sel = std::abs(hashCode(_hostname) % (int)readySources.size());
        nextFRTConnection = readySources[sel];
    } else {
        int sel = std::abs(hashCode(_hostname) % (int)suspendedSources.size());
        nextFRTConnection = suspendedSources[sel];
    }
    return nextFRTConnection;
}



const std::vector<FRTConnection *> &
FRTConnectionPool::getReadySources(std::vector<FRTConnection*> & readySources) const
{
    readySources.clear();
    for (const auto & entry : _connections) {
        FRTConnection* source = entry.second.get();
        int64_t tnow = FRTConnection::milliSecsSinceEpoch();
        int64_t timestamp = tnow;
        if (source->getSuspendedUntil() < timestamp) {
            readySources.push_back(source);
        }
    }
    return readySources;
}

const std::vector<FRTConnection *> &
FRTConnectionPool::getSuspendedSources(std::vector<FRTConnection*> & suspendedSources) const
{
    suspendedSources.clear();
    for (const auto & entry : _connections) {
        FRTConnection* source = entry.second.get();
        int64_t tnow = FRTConnection::milliSecsSinceEpoch();
        int64_t timestamp = tnow;
        if (source->getSuspendedUntil() >= timestamp) {
            suspendedSources.push_back(source);
        }
    }
    return suspendedSources;
}

int FRTConnectionPool::hashCode(const vespalib::string & s)
{
    int hashval = 0;

    for (int i = 0; i < (int)s.length(); i++) {
        hashval = 31 * hashval + s[i];
    }
    return hashval;
}

void
FRTConnectionPool::setHostname()
{
    _hostname = vespalib::HostName::get();
}

FNET_Scheduler *
FRTConnectionPool::getScheduler() {
    return _supervisor->GetScheduler();
}

}
