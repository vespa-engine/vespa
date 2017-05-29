// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visible_map.h"

namespace slobrok {

class NamedService;
class ManagedRpcServer;
class RemoteRpcServer;
class ReservedName;

/**
 * @class RpcServerMap
 * @brief Contains the actual collections of NamedService (and subclasses)
 *        objects known by this location broker.
 *
 * Works as a collection of NamedService objects, but actually contains
 * three seperate hashmaps.
 **/

using vespalib::HashMap;

class RpcServerMap
{
private:
    VisibleMap                   _visible_map;

    HashMap<ManagedRpcServer *>  _myrpcsrv_map;

    HashMap<ReservedName *> _reservations;

    static bool match(const char *name, const char *pattern);

    RpcServerMap(const RpcServerMap &);            // Not used
    RpcServerMap &operator=(const RpcServerMap &); // Not use

    void add(NamedService *rpcsrv);

public:
    typedef std::vector<const NamedService *> RpcSrvlist;

    VisibleMap& visibleMap() { return _visible_map; }

    ManagedRpcServer *lookupManaged(const char *name) const {
        return _myrpcsrv_map[name];
    }

    NamedService *    lookup(const char *name) const;
    RpcSrvlist        lookupPattern(const char *pattern) const;
    RpcSrvlist        allVisible() const;
    RpcSrvlist        allManaged() const;

    void              addNew(ManagedRpcServer *rpcsrv);
    NamedService *    remove(const char *name);

    void          addReservation(ReservedName *rpcsrv);
    bool          conflictingReservation(const char *name, const char *spec);

    ReservedName *getReservation(const char *name) const {
        return _reservations[name];
    }
    void removeReservation(const char *name);

    RpcServerMap()
        : _myrpcsrv_map(NULL),
          _reservations(NULL)
    {
    }
    ~RpcServerMap();
};

//-----------------------------------------------------------------------------

} // namespace slobrok

