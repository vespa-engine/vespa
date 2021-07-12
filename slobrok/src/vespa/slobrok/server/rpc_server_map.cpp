// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_server_map.h"
#include "reserved_name.h"
#include "rpc_server_manager.h"
#include "sbenv.h"

#include <vespa/log/log.h>
LOG_SETUP(".slobrok.server.rpc_server_map");

namespace slobrok {

//-----------------------------------------------------------------------------

ManagedRpcServer *
RpcServerMap::lookupManaged(const std::string & name) const {
    auto found = _myrpcsrv_map.find(name);
    return (found == _myrpcsrv_map.end()) ? nullptr : found->second.get();
}

const NamedService *
RpcServerMap::lookup(const std::string & name) const
{
    return lookupManaged(name);
}

std::unique_ptr<NamedService>
RpcServerMap::remove(const std::string & name)
{
    _visible_map.remove(name);
    auto service = std::move(_myrpcsrv_map[name]);
    _myrpcsrv_map.erase(name);
    return service;
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
    const std::string &name = rpcsrv->getName();

    LOG_ASSERT(rpcsrv != nullptr);
    LOG_ASSERT(_myrpcsrv_map.find(name) == _myrpcsrv_map.end());

    removeReservation(name);
    _visible_map.update(ServiceMapping{name, rpcsrv->getSpec()});
}

void
RpcServerMap::addNew(std::unique_ptr<ManagedRpcServer> rpcsrv)
{
    const std::string &name = rpcsrv->getName();

    auto oldman = std::move(_myrpcsrv_map[name]);
    _myrpcsrv_map.erase(name);

    if (oldman) {
        const ReservedName *oldres = _reservations[name].get();
        _visible_map.remove(name);

        const std::string &spec = rpcsrv->getSpec();
        const std::string &oldname = oldman->getName();
        const std::string &oldspec = oldman->getSpec();
        if (spec != oldspec)  {
            LOG(warning, "internal state problem: adding [%s at %s] but already had [%s at %s]",
                name.c_str(), spec.c_str(), oldname.c_str(), oldspec.c_str());
            if (oldres != nullptr) {
                const std::string &n = oldres->getName();
                const std::string &s = oldres->getSpec();
                LOG(warning, "old reservation: [%s at %s]", n.c_str(), s.c_str());
            }
        }
    }
    add(rpcsrv.get());
    _myrpcsrv_map[name] = std::move(rpcsrv);
}


void
RpcServerMap::addReservation(std::unique_ptr<ReservedName> rpcsrv)
{
    LOG_ASSERT(rpcsrv != nullptr);
    LOG_ASSERT(_myrpcsrv_map.find(rpcsrv->getName()) == _myrpcsrv_map.end());

    // must not be reserved for something else already
    // this should have been checked already, so assert
    LOG_ASSERT(! conflictingReservation(rpcsrv->getName(), rpcsrv->getSpec()));
    auto old = std::move(_reservations[rpcsrv->getName()]);
    LOG_ASSERT(!old
               || old->getSpec() == rpcsrv->getSpec()
               || ! old->stillReserved());
    _reservations[rpcsrv->getName()] = std::move(rpcsrv);
}


/** check if there is a (different) registration for this name in progress */
bool
RpcServerMap::conflictingReservation(const std::string &name, const std::string &spec)
{
    const ReservedName *resv = _reservations[name].get();
    return (resv != nullptr &&
            resv->stillReserved() &&
            resv->getSpec() !=  spec);
}

const ReservedName *
RpcServerMap::getReservation(const std::string &name) const {
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
RpcServerMap::removeReservation(const std::string & name)
{
    _reservations.erase(name);
}

} // namespace slobrok
