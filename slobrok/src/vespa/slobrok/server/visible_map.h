// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "history.h"
#include "named_service.h"
#include <unordered_map>
#include <string>
#include <memory>

namespace slobrok {

/**
 * @class VisibleMap
 * @brief API to the collection of NamedService
 *        name->spec mappings visible to the world
 **/

class VisibleMap
{
public:
    class IUpdateListener
    {
    public:
        /**
         * Signals that the given RPC server map has been updated. The
         * notification will be one-shot; to get further update
         * notifications you will need to re-register the listener.
         *
         * @param map the map that became updated
         **/
        virtual void updated(VisibleMap &map) = 0;
        virtual void aborted(VisibleMap &map) = 0;
    protected:
        virtual ~IUpdateListener() {}
    };

    typedef std::vector<const NamedService *> RpcSrvlist;

    struct MapDiff
    {
        MapDiff();
        ~MapDiff();
        std::vector<std::string> removed;
        RpcSrvlist               updated;
    };

private:
    using Map = std::unordered_map<std::string, const NamedService *>;
    using WaitList = std::vector<IUpdateListener *>;

    Map              _map;
    WaitList         _waitList;
    vespalib::GenCnt _genCnt;
    History          _history;

    static bool match(const char *name, const char *pattern);

    void updated();
    void aborted();

public:
    void addUpdateListener(IUpdateListener *l);
    void removeUpdateListener(IUpdateListener *l);

    void       addNew(const NamedService *rpcsrv);
    const NamedService *remove(const std::string &name);
    const NamedService *update(const NamedService *rpcsrv);

    const NamedService *lookup(const std::string &name) const;
    RpcSrvlist allVisible() const;

    const vespalib::GenCnt& genCnt() { return _genCnt; }

    bool hasHistory(vespalib::GenCnt gen) const { return _history.has(gen); }

    MapDiff history(const vespalib::GenCnt& gen) const;

    VisibleMap(const VisibleMap &) = delete;
    VisibleMap &operator=(const VisibleMap &) = delete;
    VisibleMap();
    ~VisibleMap();
};

//-----------------------------------------------------------------------------

} // namespace slobrok

