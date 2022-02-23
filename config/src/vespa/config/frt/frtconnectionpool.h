// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "frtconnection.h"
#include "connectionfactory.h"
#include <vespa/config/subscription/sourcespec.h>
#include <vector>
#include <map>

class FNET_Transport;
class FastOS_ThreadPool;

namespace config {

class FRTConnectionPool : public ConnectionFactory {
private:

    /**
     * This class makes it possible to iterate over the entries in the
     * connections map in the order they were inserted. Used to keep
     * consistency with the Java version that uses LinkedHashMap.
     */
    class FRTConnectionKey {
    private:
        int _idx;
        vespalib::string _hostname;
    public:
        FRTConnectionKey() : FRTConnectionKey(0, "") {}
        FRTConnectionKey(int idx, const vespalib::string& hostname);
        int operator<(const FRTConnectionKey& right) const;
        int operator==(const FRTConnectionKey& right) const;
    };

    std::unique_ptr<FRT_Supervisor> _supervisor;
    int _selectIdx;
    vespalib::string _hostname;
    using ConnectionMap = std::map<FRTConnectionKey, FRTConnection::SP>;
    ConnectionMap _connections;

public:
    FRTConnectionPool(FNET_Transport & transport, const ServerSpec & spec, const TimingValues & timingValues);
    FRTConnectionPool(const FRTConnectionPool&) = delete;
    FRTConnectionPool& operator=(const FRTConnectionPool&) = delete;
    ~FRTConnectionPool() override;

    void syncTransport() override;

    /**
     * Sets the hostname to the host where this program is running.
     */
    void setHostname();

    /**
     * Sets the hostname.
     *
     * @param hostname the hostname
     */
    void setHostname(const vespalib::string & hostname) { _hostname = hostname; }

    FNET_Scheduler * getScheduler() override;

    /**
     * Returns the current FRTConnection instance, taken from the list of error-free sources.
     * If no sources are error-free, an instance from the list of sources with errors
     * is returned.
     *
     * @return The next FRTConnection instance in the list.
     */
    Connection* getCurrent() override;

    /**
     * Returns the next FRTConnection instance from the list of error-free sources in a round robin
     * fashion. If no sources are error-free, an instance from the list of sources with errors
     * is returned.
     *
     * @return The next FRTConnection instance in the list.
     */
    FRTConnection* getNextRoundRobin();

    /**
     * Returns the current FRTConnection instance from the list of error-free sources, based on the
     * hostname where this program is currently running. If no sources are error-free, an instance
     * from the list of sources with errors is returned.
     *
     * @return The next FRTConnection instance in the list.
     */
    FRTConnection* getNextHashBased();

    /**
     * Gets list of sources that are not suspended.
     *
     * @return list of FRTConnection pointers
     */
    std::vector<FRTConnection*> getReadySources() const;

    /**
     * Gets list of sources that are suspended.
     *
     * @param suspendedSources is list of FRTConnection pointers
     */
    std::vector<FRTConnection*> getSuspendedSources() const;
};

class FRTConnectionPoolWithTransport : public ConnectionFactory {
public:
    FRTConnectionPoolWithTransport(std::unique_ptr<FastOS_ThreadPool> threadPool,
                                   std::unique_ptr<FNET_Transport> transport,
                                   const ServerSpec & spec, const TimingValues & timingValues);
    FRTConnectionPoolWithTransport(const FRTConnectionPoolWithTransport&) = delete;
    FRTConnectionPoolWithTransport& operator=(const FRTConnectionPoolWithTransport&) = delete;
    ~FRTConnectionPoolWithTransport() override;
    FNET_Scheduler * getScheduler() override { return _connectionPool->getScheduler(); }
    void syncTransport() override { _connectionPool->syncTransport(); }
    Connection* getCurrent() override { return _connectionPool->getCurrent(); }
private:
    std::unique_ptr<FastOS_ThreadPool> _threadPool;
    std::unique_ptr<FNET_Transport>    _transport;
    std::unique_ptr<FRTConnectionPool> _connectionPool;
};

} // namespace config

