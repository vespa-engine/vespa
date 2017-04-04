// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "frtconnection.h"
#include "connectionfactory.h"
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/fnet/frt/frt.h>
#include <vector>
#include <string>
#include <map>
namespace config {

class FRTConnectionPool : public ConnectionFactory {

private:
    FRTConnectionPool(const FRTConnectionPool&);
    FRTConnectionPool& operator=(const FRTConnectionPool&);

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

    FRT_Supervisor _supervisor;
    int _selectIdx;
    vespalib::string _hostname;
    typedef std::map<FRTConnectionKey, FRTConnection::SP> ConnectionMap;
    ConnectionMap _connections;

public:
    FRTConnectionPool(const ServerSpec & spec, const TimingValues & timingValues);
    ~FRTConnectionPool();

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

    FNET_Scheduler * getScheduler() override { return _supervisor.GetScheduler(); }

    /**
     * Gets the hostname.
     *
     * @return the hostname
     */
    vespalib::string & getHostname() { return _hostname; }

    /**
     * Trim away leading and trailing spaces.
     *
     * @param s the string to trim away spaces from
     * @return string without leading or trailing spaces
     */
    vespalib::string trim(vespalib::string s);

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
    const std::vector<FRTConnection*> & getReadySources(std::vector<FRTConnection*> & readySources) const;

    /**
     * Gets list of sources that are suspended.
     *
     * @param suspendedSources is list of FRTConnection pointers
     */
    const std::vector<FRTConnection*> & getSuspendedSources(std::vector<FRTConnection*> & suspendedSources) const;

    /**
     * Implementation of the Java hashCode function for the String class.
     *
     * Ensures that the same hostname maps to the same configserver/proxy
     * for both language implementations.
     *
     * @param s the string to compute the hash from
     * @return the hash value
     */
    static int hashCode(const vespalib::string & s);
};

} // namespace config

