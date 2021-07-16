// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "named_service.h"
#include "service_map_history.h"
#include "proxy_map_source.h"

#include <memory>
#include <string>
#include <unordered_map>

namespace slobrok {

class NamedService;
class ManagedRpcServer;
class ReservedName;

/**
 * @class RpcServerMap
 * @brief Contains the actual collections of NamedService (and subclasses)
 *        objects known by this location broker.
 *
 * Works as a collection of NamedService objects, but actually contains
 * three seperate hashmaps.
 **/

class RpcServerMap
{
private:
    using ManagedRpcServerMap = std::unordered_map<std::string, std::unique_ptr<ManagedRpcServer>>;
    using ReservedNameMap = std::unordered_map<std::string, std::unique_ptr<ReservedName>>;
    ManagedRpcServerMap  _myrpcsrv_map;
    ReservedNameMap      _reservations;
    ProxyMapSource      _proxy;

    static bool match(const char *name, const char *pattern);

    void add(NamedService *rpcsrv);

public:
    typedef std::vector<const NamedService *> RpcSrvlist;

    MapSource &proxy() { return _proxy; }

    ManagedRpcServer *lookupManaged(const std::string & name) const;

    const NamedService *    lookup(const std::string & name) const;
    RpcSrvlist        lookupPattern(const char *pattern) const;
    RpcSrvlist        allManaged() const;

    void              addNew(std::unique_ptr<ManagedRpcServer> rpcsrv);
    std::unique_ptr<NamedService> remove(const std::string & name);

    void          addReservation(std::unique_ptr<ReservedName>rpcsrv);
    bool          conflictingReservation(const std::string & name, const std::string &spec);

    const ReservedName *getReservation(const std::string & name) const;
    void removeReservation(const std::string & name);

    RpcServerMap(const RpcServerMap &) = delete;
    RpcServerMap &operator=(const RpcServerMap &) = delete;
    RpcServerMap();
    ~RpcServerMap();
};

//-----------------------------------------------------------------------------

} // namespace slobrok

