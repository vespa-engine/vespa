// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_server_map.h"
#include "reserved_name.h"
#include "rpc_server_manager.h"
#include "sbenv.h"

#include <vespa/log/log.h>
LOG_SETUP(".rpcsrvmap");

namespace slobrok {

//-----------------------------------------------------------------------------

ManagedRpcServer *
RpcServerMap::lookupManaged(const char *name) const {
    auto found = _myrpcsrv_map.find(name);
    return (found == _myrpcsrv_map.end()) ? nullptr : found->second.get();
}

NamedService *
RpcServerMap::lookup(const char *name) const
{
    return lookupManaged(name);
}

NamedService *
RpcServerMap::remove(const char *name)
{
    _visible_map.remove(name);
    auto service = std::move(_myrpcsrv_map[name]);
    _myrpcsrv_map.erase(name);
    return service.release();
}

std::vector<const NamedService *>
RpcServerMap::lookupPattern(const char *pattern) const
{
    std::vector<const NamedService *> retval;
    for (const auto & entry : _myrpcsrv_map) {
        if (match(entry.first.c_str(), pattern)) {
            retval.push_back(entry.second.get());
        }
    }
    return retval;
}


std::vector<const NamedService *>
RpcServerMap::allManaged() const
{
    std::vector<const NamedService *> retval;
    // get list of all names in myrpcsrv_map
    for (const auto & entry : _myrpcsrv_map) {
        retval.push_back(entry.second.get());
    }
    return retval;
}


void
RpcServerMap::add(NamedService *rpcsrv)
{
    const char *name = rpcsrv->getName();

    LOG_ASSERT(rpcsrv != nullptr);
    LOG_ASSERT(_myrpcsrv_map.find(name) == _myrpcsrv_map.end());

    removeReservation(name);

    LOG_ASSERT(_visible_map.lookup(name) == nullptr);
    _visible_map.addNew(rpcsrv);
}

void
RpcServerMap::addNew(ManagedRpcServer *rpcsrv)
{
    const char *name = rpcsrv->getName();

    auto oldman = std::move(_myrpcsrv_map[name]);
    _myrpcsrv_map.erase(name);

    if (oldman) {
        const ReservedName *oldres = _reservations[name].get();
        const NamedService *oldvis = _visible_map.remove(name);

        const char *spec = rpcsrv->getSpec();
        const char *oldname = oldman->getName();
        const char *oldspec = oldman->getSpec();
        if (strcmp(spec, oldspec) != 0)  {
            LOG(warning, "internal state problem: adding [%s at %s] but already had [%s at %s]",
                name, spec, oldname, oldspec);
            if (oldvis != oldman.get()) {
                const char *n = oldvis->getName();
                const char *s = oldvis->getSpec();
                LOG(warning, "BAD: different old visible: [%s at %s]", n, s);
            }
            if (oldres != nullptr) {
                const char *n = oldres->getName();
                const char *s = oldres->getSpec();
                LOG(warning, "old reservation: [%s at %s]", n, s);
            }
        }
    }
    add(rpcsrv);
    _myrpcsrv_map[name].reset(rpcsrv);
}


void
RpcServerMap::addReservation(ReservedName *rpcsrv)
{
    LOG_ASSERT(rpcsrv != nullptr);
    LOG_ASSERT(_myrpcsrv_map.find(rpcsrv->getName()) == _myrpcsrv_map.end());

    // must not be reserved for something else already
    // this should have been checked already, so assert
    LOG_ASSERT(! conflictingReservation(rpcsrv->getName(), rpcsrv->getSpec()));
    auto old = std::move(_reservations[rpcsrv->getName()]);
    _reservations[rpcsrv->getName()].reset(rpcsrv);
    LOG_ASSERT(!old
               || strcmp(old->getSpec(), rpcsrv->getSpec()) == 0
               || ! old->stillReserved());
}


/** check if there is a (different) registration for this name in progress */
bool
RpcServerMap::conflictingReservation(const char *name, const char *spec)
{
    const ReservedName *resv = _reservations[name].get();
    return (resv != nullptr &&
            resv->stillReserved() &&
            strcmp(resv->getSpec(), spec) != 0);
}

const ReservedName *
RpcServerMap::getReservation(const char *name) const {
    auto found = _reservations.find(name);
    return (found == _reservations.end()) ? nullptr : found->second.get();
}

RpcServerMap::RpcServerMap()
    : _myrpcsrv_map(),
      _reservations()
{
}

RpcServerMap::~RpcServerMap() = default;

bool
RpcServerMap::match(const char *name, const char *pattern)
{
    LOG_ASSERT(name != nullptr);
    LOG_ASSERT(pattern != nullptr);
    while (*pattern != '\0') {
        if (*name == *pattern) {
            ++name;
            ++pattern;
        } else if (*pattern == '*') {
            ++pattern;
            while (*name != '/' && *name != '\0') {
                ++name;
            }
        } else {
            return false;
        }
    }
    return (*name == *pattern);
}

void
RpcServerMap::removeReservation(const char *name)
{
    _reservations.erase(name);
}

} // namespace slobrok
