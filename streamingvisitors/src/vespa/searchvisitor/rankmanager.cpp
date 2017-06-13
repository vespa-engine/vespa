// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rankmanager.h"
#include <vespa/searchlib/features/setup.h>
#include <vespa/searchlib/fef/functiontablefactory.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exception.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchvisitor.rankmanager");

using vespa::config::search::RankProfilesConfig;
using vespa::config::search::vsm::VsmfieldsConfig;
using search::fef::Blueprint;
using search::fef::BlueprintFactory;
using search::fef::FieldInfo;
using search::fef::Properties;
using search::fef::RankSetup;
using vsm::VsmfieldsHandle;
using vsm::VSMAdapter;

namespace storage {

void
RankManager::Snapshot::addProperties(const vespa::config::search::RankProfilesConfig & cfg)
{
    for (uint32_t i = 0; i < cfg.rankprofile.size(); ++i) {
        const RankProfilesConfig::Rankprofile & curr = cfg.rankprofile[i];
        _properties.push_back(NamedPropertySet());
        _properties.back().first = curr.name;
        Properties & p = _properties.back().second;
        for (uint32_t j = 0; j < curr.fef.property.size(); ++j) {
            p.add(vespalib::string(curr.fef.property[j].name.c_str()),
                  vespalib::string(curr.fef.property[j].value.c_str()));
        }
    }
}

void
RankManager::Snapshot::detectFields(const VsmfieldsHandle & fields)
{
    for (uint32_t i = 0; i < fields->fieldspec.size(); ++i) {
        const VsmfieldsConfig::Fieldspec & fs = fields->fieldspec[i];
        bool isAttribute = (fs.fieldtype == VsmfieldsConfig::Fieldspec::ATTRIBUTE);
        LOG(debug, "Adding field of type '%s' and name '%s' with id '%u' the index environment.",
                   isAttribute ? "ATTRIBUTE" : "INDEX", fs.name.c_str(), i);
        // This id must match the vsm specific field id
        _protoEnv.addField(fs.name, isAttribute);
    }
}

void
RankManager::Snapshot::buildFieldMappings(const VsmfieldsHandle & fields)
{
    for (uint32_t i = 0; i < fields->documenttype.size(); ++i) {
        const char * dname = fields->documenttype[i].name.c_str();
        LOG(debug, "Looking through indexes for documenttype '%s'", dname);
        for (uint32_t j = 0; j < fields->documenttype[i].index.size(); ++j) {
            const char * iname = fields->documenttype[i].index[j].name.c_str();
            LOG(debug, "Looking through fields for index '%s'", iname);
            View view;
            for (uint32_t k = 0; k < fields->documenttype[i].index[j].field.size(); ++k) {
                const char * fname = fields->documenttype[i].index[j].field[k].name.c_str();
                const FieldInfo * info = _protoEnv.getFieldByName(vespalib::string(fname));
                if (info != NULL) {
                    LOG(debug, "Adding field '%s' to view in index '%s' (field id '%u')",
                        fname, iname, info->id());
                    view.push_back(info->id());
                } else {
                    LOG(warning, "Field '%s' is not registred in the index environment. "
                        "Cannot add to index view.", fname);
                }
            }
            if (_views.find(iname) == _views.end()) {
                std::sort(view.begin(), view.end()); // lowest field id first
                _views[iname] = view;
            } else {
                LOG(warning, "We already have a view for index '%s'. Drop the new view.", iname);
            }
        }
    }
}

bool
RankManager::Snapshot::initRankSetup(const BlueprintFactory & factory)
{
    // set up individual index environments per rank profile
    for (uint32_t i = 0; i < _properties.size(); ++i) {
        _indexEnv.push_back(_protoEnv);
        IndexEnvironment & ie = _indexEnv.back();
        ie.getProperties().import(_properties[i].second);
    }

    // set up individual rank setups per rank profile
    for (uint32_t i = 0; i < _indexEnv.size(); ++i) {
        IndexEnvironment & ie = _indexEnv[i];

        RankSetup::SP rs(new RankSetup(factory, ie));
        rs->configure(); // reads config values from the property map
        if (!rs->compile()) {
            LOG(warning, "Could not compile rank setup for rank profile '%u'.", i);
            return false;
        }
        _rankSetup.push_back(rs);
    }
    LOG_ASSERT(_indexEnv.size() == _rankSetup.size());
    LOG(debug, "Number of index environments and rank setups: %u", (uint32_t)_indexEnv.size());
    LOG_ASSERT(_properties.size() == _rankSetup.size());
    for (uint32_t i = 0; i < _properties.size(); ++i) {
        vespalib::string number = vespalib::make_string("%u", i);
        _rpmap[number] = i;
    }
    for (uint32_t i = 0; i < _properties.size(); ++i) {
        const vespalib::string &name = _properties[i].first;
        _rpmap[name] = i;
    }
    return true;
}

RankManager::Snapshot::Snapshot() :
    _tableManager(),
    _protoEnv(_tableManager),
    _properties(),
    _indexEnv(),
    _rankSetup(),
    _rpmap(),
    _views()
{
    _tableManager.addFactory(search::fef::ITableFactory::SP(new search::fef::FunctionTableFactory(256)));
}

bool
RankManager::Snapshot::setup(const RankManager & rm, const std::vector<NamedPropertySet> & properties)
{
    _properties = properties;
    return setup(rm);
}

bool
RankManager::Snapshot::setup(const RankManager & rm)
{
    VsmfieldsHandle fields = rm._vsmAdapter->getFieldsConfig();
    detectFields(fields);
    buildFieldMappings(fields);
    if (!initRankSetup(rm._blueprintFactory)) {
        return false;
    }
    return true;
}

bool
RankManager::Snapshot::setup(const RankManager & rm, const RankProfilesConfig & cfg)
{
    addProperties(cfg);
    return setup(rm);
}

void RankManager::notify(const vsm::VSMConfigSnapshot & snap)
{
    configureRankProfiles(*snap.getConfig<RankProfilesConfig>());
}


void
RankManager::configureRankProfiles(const RankProfilesConfig & cfg)
{
    LOG(debug, "configureRankProfiles(): Size of cfg rankprofiles: %zd", cfg.rankprofile.size());

    std::unique_ptr<Snapshot> snapshot(new Snapshot());
    if (snapshot->setup(*this, cfg)) {
        _snapshot.set(snapshot.release());
        _snapshot.latch(); // switch to the new config object
    } else {
        vespalib::string msg = "(re-)configuration of rank manager failed";
        LOG(error, "%s", msg.c_str());
        throw vespalib::Exception(msg, VESPA_STRLOC);
    }
}

RankManager::RankManager(VSMAdapter * const vsmAdapter) :
    _blueprintFactory(),
    _snapshot(),
    _vsmAdapter(vsmAdapter)
{
    // init blueprint factory
    search::features::setup_search_features(_blueprintFactory);
}

RankManager::~RankManager()
{
}

void
RankManager::configure(const vsm::VSMConfigSnapshot & snap)
{
    notify(snap);
}


} // namespace storage

