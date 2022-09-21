// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultconfig.h"
#include "docsum_field_writer.h"
#include "docsum_field_writer_factory.h"
#include "resultclass.h"
#include <vespa/config-summary.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/exceptions.h>
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
      _classLookup(),
      _nameLookup()
{
}


ResultConfig::~ResultConfig()
{
    Clean();
}


void
ResultConfig::reset()
{
    if (! _classLookup.empty()) {
        Clean();
    }
}


ResultClass *
ResultConfig::addResultClass(const char *name, uint32_t classID)
{
    ResultClass *ret = nullptr;

    if (classID != noClassID() && (_classLookup.find(classID) == _classLookup.end())) {
        auto rc = std::make_unique<ResultClass>(name);
        ret = rc.get();
        _classLookup[classID] = std::move(rc);
        if (_nameLookup.find(name) != _nameLookup.end()) {
            LOG(warning, "Duplicate result class name: %s (now maps to class id %u)", name, classID);
        }
        _nameLookup[name] = classID;
    }
    return ret;
}

void
ResultConfig::set_default_result_class_id(uint32_t id)
{
    _defaultSummaryId = id;
}

const ResultClass*
ResultConfig::lookupResultClass(uint32_t classID) const
{
    auto it = _classLookup.find(classID);
    return (it != _classLookup.end()) ? it->second.get() : nullptr;
}

uint32_t
ResultConfig::lookupResultClassId(const vespalib::string &name) const
{
    auto found = _nameLookup.find(name);
    return (found != _nameLookup.end()) ? found->second : ((name.empty() || (name == "default")) ? _defaultSummaryId : noClassID());
}


namespace {
std::atomic<bool> global_useV8geoPositions = false;
}

bool ResultConfig::wantedV8geoPositions() {
    return global_useV8geoPositions;
}

void
ResultConfig::set_wanted_v8_geo_positions(bool value)
{
    global_useV8geoPositions = value;
}

bool
ResultConfig::readConfig(const SummaryConfig &cfg, const char *configId, IDocsumFieldWriterFactory& docsum_field_writer_factory)
{
    bool rc = true;
    reset();
    int    maxclassID = 0x7fffffff; // avoid negative classids
    _defaultSummaryId = cfg.defaultsummaryid;
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
        ResultClass *resClass = addResultClass(cfg_class.name.c_str(), classID);
        if (resClass == nullptr) {
            LOG(error,"%s: unable to add classes[%d] name %s", configId, i, cfg_class.name.c_str());
            rc = false;
            break;
        }
        resClass->set_omit_summary_features(cfg_class.omitsummaryfeatures);
        for (const auto & field : cfg_class.fields) {
            const char *fieldname = field.name.c_str();
            vespalib::string command = field.command;
            vespalib::string source_name = field.source;
            LOG(debug, "Reconfiguring class '%s' field '%s'", cfg_class.name.c_str(), fieldname);
            std::unique_ptr<DocsumFieldWriter> docsum_field_writer;
            if (!command.empty()) {
                try {
                    docsum_field_writer = docsum_field_writer_factory.create_docsum_field_writer(fieldname,
                                                                                                 command,
                                                                                                 source_name);
                } catch (const vespalib::IllegalArgumentException& ex) {
                    LOG(error, "Exception during setup of summary result class '%s': field='%s', command='%s', source='%s': %s",
                        cfg_class.name.c_str(), fieldname, command.c_str(), source_name.c_str(), ex.getMessage().c_str());
                    break;
                }
            }
            rc = resClass->addConfigEntry(fieldname, std::move(docsum_field_writer));
            if (!rc) {
                LOG(error, "%s %s.fields: duplicate name '%s'", configId, cfg_class.name.c_str(), fieldname);
                break;
            }
        }
    }
    if (!rc) {
        reset();          // FAIL, discard all config
    }
    return rc;
}

}
