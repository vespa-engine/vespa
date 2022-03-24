// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "frtconnectionpool.h"
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>

#include <vespa/log/log.h>
LOG_SETUP(".config.frt.frtconnectionpool");

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

FRTConnectionPool::FRTConnectionPool(FNET_Transport & transport, const ServerSpec & spec, const TimingValues & timingValues)
    : _supervisor(std::make_unique<FRT_Supervisor>(& transport)),
      _selectIdx(0),
      _hostname("")
{
    for (size_t i(0); i < spec.numHosts(); i++) {
        FRTConnectionKey key(i, spec.getHost(i));
        _connections[key] = std::make_shared<FRTConnection>(spec.getHost(i), *_supervisor, timingValues);
    }
    setHostname();
}

FRTConnectionPool::~FRTConnectionPool() {
    LOG(debug, "Shutting down %lu connections", _connections.size());
    syncTransport();
    _connections.clear();
    syncTransport();
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
    auto ready = getReadySources();
    auto suspended = getSuspendedSources();
    FRTConnection* nextFRTConnection = nullptr;

    if ( ! ready.empty()) {
        unsigned int sel = _selectIdx % (int)ready.size();
        LOG_ASSERT(sel < ready.size());
        _selectIdx = sel + 1;
        nextFRTConnection = ready[sel];
    } else if ( ! suspended.empty()) {
        unsigned int sel = _selectIdx % (int)suspended.size();
        LOG_ASSERT(sel < suspended.size());
        _selectIdx = sel + 1;
        nextFRTConnection = suspended[sel];
    }
    return nextFRTConnection;
}

namespace {
/**
 * Implementation of the Java hashCode function for the String class.
 *
 * Ensures that the same hostname maps to the same configserver/proxy
 * for both language implementations.
 *
 * @param s the string to compute the hash from
 * @return the hash value
 */
int hashCode(const vespalib::string & s) {
    unsigned int hashval = 0;

    for (int i = 0; i < (int) s.length(); i++) {
        hashval = 31 * hashval + s[i];
    }
    return hashval;
}

}

FRTConnection *
FRTConnectionPool::getNextHashBased()
{
    auto ready = getReadySources();
    auto suspended = getSuspendedSources();
    FRTConnection* nextFRTConnection = nullptr;

    if ( ! ready.empty()) {
        unsigned int sel = std::abs(hashCode(_hostname) % (int)ready.size());
        LOG_ASSERT(sel < ready.size());
        nextFRTConnection = ready[sel];
    } else if ( ! suspended.empty() ){
        unsigned int sel = std::abs(hashCode(_hostname) % (int)suspended.size());
        LOG_ASSERT(sel < suspended.size());
        nextFRTConnection = suspended[sel];
    }
    return nextFRTConnection;
}



std::vector<FRTConnection *>
FRTConnectionPool::getReadySources() const
{
    std::vector<FRTConnection*> readySources;
    for (const auto & entry : _connections) {
        FRTConnection* source = entry.second.get();
        vespalib::system_time timestamp = vespalib::system_clock::now();
        if (source->getSuspendedUntil() < timestamp) {
            readySources.push_back(source);
        }
    }
    return readySources;
}

std::vector<FRTConnection *>
FRTConnectionPool::getSuspendedSources() const
{
    std::vector<FRTConnection*> suspendedSources;
    for (const auto & entry : _connections) {
        FRTConnection* source = entry.second.get();
        vespalib::system_time timestamp = vespalib::system_clock::now();
        if (source->getSuspendedUntil() >= timestamp) {
            suspendedSources.push_back(source);
        }
    }
    return suspendedSources;
}

void
FRTConnectionPool::setHostname()
{
    setHostname(vespalib::HostName::get());
}

FNET_Scheduler *
FRTConnectionPool::getScheduler() {
    return _supervisor->GetScheduler();
}

FRTConnectionPoolWithTransport::FRTConnectionPoolWithTransport(std::unique_ptr<FastOS_ThreadPool> threadPool,
                                                               std::unique_ptr<FNET_Transport> transport,
                                                               const ServerSpec & spec, const TimingValues & timingValues)
    :  _threadPool(std::move(threadPool)),
       _transport(std::move(transport)),
       _connectionPool(std::make_unique<FRTConnectionPool>(*_transport, spec, timingValues))
{
    _transport->Start(_threadPool.get());
}

FRTConnectionPoolWithTransport::~FRTConnectionPoolWithTransport()
{
    syncTransport();
    _transport->ShutDown(true);
}

}
