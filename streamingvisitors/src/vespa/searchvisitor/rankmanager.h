// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "indexenvironment.h"
#include <vespa/config-rank-profiles.h>
#include <vespa/searchlib/fef/blueprintfactory.h>
#include <vespa/searchlib/fef/ranksetup.h>
#include <vespa/searchlib/fef/tablemanager.h>
#include <vespa/vsm/vsm/vsm-adapter.h>

namespace storage {

/**
 * This class subscribes to the rank-profiles config and keeps a setup per rank profile.
 **/
class RankManager
{
public:
    /** collection of field ids for an index **/
    typedef std::vector<uint32_t> View;

    /**
     * This class represents a snapshot of the rank-profiles config with associated setup per rank profile.
     * A new instance of this class is created as part of reload config.
     **/
    class Snapshot {
    private:
        typedef std::pair<vespalib::string, search::fef::Properties> NamedPropertySet;
        typedef vespalib::hash_map<vespalib::string, View> ViewMap;
        typedef vespalib::hash_map<vespalib::string, int> Map;
        search::fef::TableManager                 _tableManager;
        IndexEnvironment                          _protoEnv;
        std::vector<NamedPropertySet>             _properties; // property set per rank profile
        std::vector<IndexEnvironment>             _indexEnv;   // index environment per rank profile
        std::vector<search::fef::RankSetup::SP>   _rankSetup;  // rank setup per rank profile
        Map                                       _rpmap;
        ViewMap                                   _views;

        void addProperties(const vespa::config::search::RankProfilesConfig & cfg);
        void detectFields(const vsm::VsmfieldsHandle & fields);
        void buildFieldMappings(const vsm::VsmfieldsHandle & fields);
        bool initRankSetup(const search::fef::BlueprintFactory & factory);
        bool setup(const RankManager & manager);
        int getIndex(const vespalib::string & key) const {
            Map::const_iterator found(_rpmap.find(key));
            return (found != _rpmap.end()) ? found->second : 0;
        }

    public:
        typedef std::shared_ptr<Snapshot> SP;
        Snapshot();
        ~Snapshot();
        const std::vector<NamedPropertySet> & getProperties() const { return _properties; }
        bool setup(const RankManager & manager, const vespa::config::search::RankProfilesConfig & cfg);
        bool setup(const RankManager & manager, const std::vector<NamedPropertySet> & properties);
        const search::fef::RankSetup & getRankSetup(const vespalib::string &rankProfile) const {
            return *(_rankSetup[getIndex(rankProfile)]);
        }
        const IndexEnvironment & getIndexEnvironment(const vespalib::string &rankProfile) const {
            return _indexEnv[getIndex(rankProfile)];
        }
        const View *getView(const vespalib::string & index) const {
            ViewMap::const_iterator itr = _views.find(index);
            if (itr != _views.end()) {
                return &itr->second;
            }
            return nullptr;
        }
    };

private:
    search::fef::BlueprintFactory _blueprintFactory;
    vespalib::PtrHolder<Snapshot> _snapshot;
    const vsm::VSMAdapter       * _vsmAdapter;

    void configureRankProfiles(const vespa::config::search::RankProfilesConfig & cfg);
    virtual void notify(const vsm::VSMConfigSnapshot & snapshot);

public:
    RankManager(vsm::VSMAdapter * const vsmAdapter);
    virtual ~RankManager();

    void configure(const vsm::VSMConfigSnapshot & snap);

    /**
     * Retrieves the current snapshot of the rank-profiles config.
     **/
    Snapshot::SP getSnapshot() const { return _snapshot.get(); }
};

} // namespace storage

