// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visible_map.h"
#include <unordered_map>
#include <string>
#include <memory>

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
    VisibleMap           _visible_map;
    ManagedRpcServerMap  _myrpcsrv_map;
    ReservedNameMap      _reservations;

    static bool match(const char *name, const char *pattern);

    void add(NamedService *rpcsrv);

public:
    typedef std::vector<const NamedService *> RpcSrvlist;

    VisibleMap& visibleMap() { return _visible_map; }

    ManagedRpcServer *lookupManaged(const char *name) const;

    NamedService *    lookup(const char *name) const;
    RpcSrvlist        lookupPattern(const char *pattern) const;
    RpcSrvlist        allVisible() const;
    RpcSrvlist        allManaged() const;

    void              addNew(ManagedRpcServer *rpcsrv);
    NamedService *    remove(const char *name);

    void          addReservation(ReservedName *rpcsrv);
    bool          conflictingReservation(const char *name, const char *spec);

    const ReservedName *getReservation(const char *name) const;
    void removeReservation(const char *name);

    RpcServerMap(const RpcServerMap &) = delete;
    RpcServerMap &operator=(const RpcServerMap &) = delete;
    RpcServerMap();
    ~RpcServerMap();
};

//-----------------------------------------------------------------------------

} // namespace slobrok

