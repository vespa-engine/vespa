// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultconfig.h"
#include "docsum_field_writer.h"
#include "docsum_field_writer_factory.h"
#include "resultclass.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <atomic>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.resultconfig");

namespace search::docsummary {

void
ResultConfig::Clean()
{
    _classLookup.clear();
    _nameLookup.clear();
}


ResultConfig::ResultConfig()
    : _defaultSummaryId(-1),
      _useV8geoPositions(false),
      _classLookup(),
      _nameLookup()
{

}


ResultConfig::~ResultConfig()
{
    Clean();
}


void
ResultConfig::Reset()
{
    if (! _classLookup.empty() || _fieldEnum.GetNumEntries() > 0) {
        Clean();
    }
}


ResultClass *
ResultConfig::AddResultClass(const char *name, uint32_t id)
{
    ResultClass *ret = nullptr;

    if (id != NoClassID() && (_classLookup.find(id) == _classLookup.end())) {
        auto rc = std::make_unique<ResultClass>(name, _fieldEnum);
        ret = rc.get();
        _classLookup[id] = std::move(rc);
        if (_nameLookup.find(name) != _nameLookup.end()) {
            LOG(warning, "Duplicate result class name: %s (now maps to class id %u)", name, id);
        }
        _nameLookup[name] = id;
    }
    return ret;
}

void
ResultConfig::set_default_result_class_id(uint32_t id)
{
    _defaultSummaryId = id;
}

const ResultClass*
ResultConfig::LookupResultClass(uint32_t id) const
{
    IdMap::const_iterator it(_classLookup.find(id));
    return (it != _classLookup.end()) ? it->second.get() : nullptr;
}

uint32_t
ResultConfig::LookupResultClassId(const vespalib::string &name) const
{
    NameMap::const_iterator found(_nameLookup.find(name));
    return (found != _nameLookup.end()) ? found->second : ((name.empty() || (name == "default")) ? _defaultSummaryId : NoClassID());
}


void
ResultConfig::CreateEnumMaps()
{
    for (auto & entry : _classLookup) {
       entry.second->CreateEnumMap();
    }
}

namespace {
std::atomic<bool> global_useV8geoPositions = false;
}

bool ResultConfig::wantedV8geoPositions() {
    return global_useV8geoPositions;
}

bool
ResultConfig::ReadConfig(const vespa::config::search::SummaryConfig &cfg, const char *configId, IDocsumFieldWriterFactory& docsum_field_writer_factory)
{
    bool rc = true;
    Reset();
    int    maxclassID = 0x7fffffff; // avoid negative classids
    _defaultSummaryId = cfg.defaultsummaryid;
    _useV8geoPositions = cfg.usev8geopositions;
    global_useV8geoPositions = cfg.usev8geopositions;

    for (uint32_t i = 0; rc && i < cfg.classes.size(); i++) {
        const auto& cfg_class = cfg.classes[i];
        if (cfg_class.name.empty()) {
            LOG(warning, "%s classes[%d]: empty name", configId, i);
        }
        int classID = cfg_class.id;
        if (classID < 0 || classID > maxclassID) {
            LOG(error, "%s classes[%d]: bad id %d", configId, i, classID);
            rc = false;
            break;
        }
        ResultClass *resClass = AddResultClass(cfg_class.name.c_str(), classID);
        if (resClass == nullptr) {
            LOG(error,"%s: unable to add classes[%d] name %s", configId, i, cfg_class.name.c_str());
            rc = false;
            break;
        }
        resClass->set_omit_summary_features(cfg_class.omitsummaryfeatures);
        for (unsigned int j = 0; rc && (j < cfg_class.fields.size()); j++) {
            const char *fieldtype = cfg_class.fields[j].type.c_str();
            const char *fieldname = cfg_class.fields[j].name.c_str();
            vespalib::string override_name = cfg_class.fields[j].command;
            vespalib::string source_name = cfg_class.fields[j].source;
            auto res_type = ResTypeUtils::get_res_type(fieldtype);
            LOG(debug, "Reconfiguring class '%s' field '%s' of type '%s'", cfg_class.name.c_str(), fieldname, fieldtype);
            if (res_type != RES_BAD) {
                std::unique_ptr<DocsumFieldWriter> docsum_field_writer;
                if (!override_name.empty()) {
                    docsum_field_writer = docsum_field_writer_factory.create_docsum_field_writer(fieldname, override_name, source_name, rc);
                    if (!rc) {
                        LOG(error, "%s override operation failed during initialization", override_name.c_str());
                        break;
                    }
                }
                rc = resClass->AddConfigEntry(fieldname, res_type, std::move(docsum_field_writer));
            } else {
                LOG(error, "%s %s.fields[%d]: unknown type '%s'", configId, cfg_class.name.c_str(), j, fieldtype);
                rc = false;
                break;
            }
            if (!rc) {
                LOG(error, "%s %s.fields[%d]: duplicate name '%s'", configId, cfg_class.name.c_str(), j, fieldname);
                break;
            }
        }
    }
    if (rc) {
        CreateEnumMaps(); // create mappings needed by TVM
    } else {
        Reset();          // FAIL, discard all config
    }
    return rc;
}

}
