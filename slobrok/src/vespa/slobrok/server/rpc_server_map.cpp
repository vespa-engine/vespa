// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rpc_server_map.h"
#include "reserved_name.h"
#include "rpc_server_manager.h"
#include "sbenv.h"

#include <vespa/log/log.h>
LOG_SETUP(".rpcsrvmap");

namespace slobrok {

//-----------------------------------------------------------------------------

NamedService *
RpcServerMap::lookup(const char *name) const
{
    NamedService *d = _myrpcsrv_map[name];
    return d;
}


NamedService *
RpcServerMap::remove(const char *name)
{
    _visible_map.remove(name);
    NamedService *d = _myrpcsrv_map.remove(name);
    return d;
}



std::vector<const NamedService *>
RpcServerMap::lookupPattern(const char *pattern) const
{
    std::vector<const NamedService *> retval;
    for (HashMap<ManagedRpcServer *>::Iterator it = _myrpcsrv_map.iterator();
         it.valid(); it.next())
    {
        if (match(it.key(), pattern)) {
            retval.push_back(it.value());
        }
    }
    return retval;
}


std::vector<const NamedService *>
RpcServerMap::allManaged() const
{
    std::vector<const NamedService *> retval;
    // get list of all names in myrpcsrv_map
    for (HashMap<ManagedRpcServer *>::Iterator it = _myrpcsrv_map.iterator();
         it.valid(); it.next())
    {
        retval.push_back(it.value());
    }
    return retval;
}


void
RpcServerMap::add(NamedService *rpcsrv)
{
    const char *name = rpcsrv->getName();

    LOG_ASSERT(rpcsrv != NULL);
    LOG_ASSERT(_myrpcsrv_map.isSet(name) == false);

    removeReservation(name);

    LOG_ASSERT(_visible_map.lookup(name) == NULL);
    _visible_map.addNew(rpcsrv);
}

void
RpcServerMap::addNew(ManagedRpcServer *rpcsrv)
{
    const char *name = rpcsrv->getName();

    ManagedRpcServer  *oldman = _myrpcsrv_map.remove(name);

    if (oldman != NULL) {
        ReservedName *oldres = _reservations[name];
        NamedService *oldvis = _visible_map.remove(name);

        const char *spec = rpcsrv->getSpec();
        const char *oldname = oldman->getName();
        const char *oldspec = oldman->getSpec();
        if (strcmp(spec, oldspec) != 0)  {
            LOG(warning, "internal state problem: adding [%s at %s] but already had [%s at %s]",
                name, spec, oldname, oldspec);
            if (oldvis != oldman) {
                const char *n = oldvis->getName();
                const char *s = oldvis->getSpec();
                LOG(warning, "BAD: different old visible: [%s at %s]", n, s);
            }
            if (oldres != NULL) {
                const char *n = oldres->getName();
                const char *s = oldres->getSpec();
                LOG(warning, "old reservation: [%s at %s]", n, s);
            }
        }
        delete oldman;
    }
    add(rpcsrv);
    _myrpcsrv_map.set(name, rpcsrv);
}


void
RpcServerMap::addReservation(ReservedName *rpcsrv)
{
    LOG_ASSERT(rpcsrv != NULL);
    LOG_ASSERT(_myrpcsrv_map.isSet(rpcsrv->getName()) == false);

    // must not be reserved for something else already
    // this should have been checked already, so assert
    LOG_ASSERT(! conflictingReservation(rpcsrv->getName(), rpcsrv->getSpec()));
    ReservedName *old = _reservations.set(rpcsrv->getName(), rpcsrv);
    LOG_ASSERT(old == NULL
               || strcmp(old->getSpec(), rpcsrv->getSpec()) == 0
               || ! old->stillReserved());
    delete old;
}


/** check if there is a (different) registration for this name in progress */
bool
RpcServerMap::conflictingReservation(const char *name, const char *spec)
{
    ReservedName *resv = _reservations[name];
    return (resv != NULL &&
            resv->stillReserved() &&
            strcmp(resv->getSpec(), spec) != 0);
}


RpcServerMap::~RpcServerMap()
{
    // get list of names in rpcsrv_map
    std::vector<const char *> names;
    for (HashMap<ManagedRpcServer *>::Iterator it = _myrpcsrv_map.iterator();
         it.valid(); it.next())
    {
        names.push_back(it.key());
    }

    for (uint32_t i = 0; i < names.size(); i++) {
        NamedService *rpcsrv = _myrpcsrv_map.remove(names[i]);
        LOG_ASSERT(rpcsrv != NULL);
        delete rpcsrv;
    }
    LOG_ASSERT(_myrpcsrv_map.size() == 0);
}


bool
RpcServerMap::match(const char *name, const char *pattern)
{
    LOG_ASSERT(name != NULL);
    LOG_ASSERT(pattern != NULL);
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
    delete _reservations.remove(name);
}


} // namespace slobrok
