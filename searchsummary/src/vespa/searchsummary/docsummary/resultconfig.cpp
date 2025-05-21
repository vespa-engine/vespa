// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultconfig.h"
#include "docsum_field_writer.h"
#include "docsum_field_writer_factory.h"
#include "resultclass.h"
#include "struct_fields_mapper.h"
#include "summary_elements_selector.h"
#include <vespa/config-summary.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <atomic>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.resultconfig");

using vespa::config::search::SummaryConfig;

namespace search::docsummary {

namespace {

SummaryElementsSelector
make_summary_elements_selector(const SummaryConfig::Classes::Fields::Elements& elements,
                               const std::string& source,
                               const StructFieldsMapper& struct_fields_mapper)
{
    using Select = SummaryConfig::Classes::Fields::Elements::Select;
    switch (elements.select) {
        case Select::BY_MATCH:
            return SummaryElementsSelector::select_by_match(source, struct_fields_mapper.get_struct_fields(source));
        case Select::BY_SUMMARY_FEATURE:
            return SummaryElementsSelector::select_by_summary_feature(elements.summaryFeature);
        case Select::ALL:
        default:
            return SummaryElementsSelector::select_all();
    }
}

}

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
ResultConfig::addResultClass(const std::string& name, uint32_t classID)
{
    ResultClass *ret = nullptr;

    if (classID != noClassID && (_classLookup.find(classID) == _classLookup.end())) {
        auto rc = std::make_unique<ResultClass>(name);
        ret = rc.get();
        _classLookup[classID] = std::move(rc);
        if (_nameLookup.find(name) != _nameLookup.end()) {
            LOG(warning, "Duplicate result class name: %s (now maps to class id %u)", name.c_str(), classID);
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
ResultConfig::lookupResultClassId(std::string_view name) const
{
    auto found = _nameLookup.find(name);
    return (found != _nameLookup.end()) ? found->second : ((name.empty() || (name == "default")) ? _defaultSummaryId : noClassID);
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
ResultConfig::readConfig(const SummaryConfig &cfg, const std::string& configId, IDocsumFieldWriterFactory& docsum_field_writer_factory,
                         const StructFieldsMapper& struct_fields_mapper)
{
    bool rc = true;
    reset();
    int    maxclassID = 0x7fffffff; // avoid negative classids
    _defaultSummaryId = cfg.defaultsummaryid;
    global_useV8geoPositions = cfg.usev8geopositions;

    ResultClass *unionOfAll = addResultClass("[all]", 0x12345678);
    auto union_of_all_matching_elements_fields = unionOfAll->get_matching_elements_fields();
    for (uint32_t i = 0; rc && i < cfg.classes.size(); i++) {
        const auto& cfg_class = cfg.classes[i];
        if (cfg_class.name.empty()) {
            LOG(warning, "%s classes[%d]: empty name", configId.c_str(), i);
        }
        int classID = cfg_class.id;
        if (classID < 0 || classID > maxclassID) {
            LOG(error, "%s classes[%d]: bad id %d", configId.c_str(), i, classID);
            rc = false;
            break;
        }
        ResultClass *resClass = addResultClass(cfg_class.name, classID);
        if (resClass == nullptr) {
            LOG(error,"%s: unable to add classes[%d] name %s", configId.c_str(), i, cfg_class.name.c_str());
            rc = false;
            break;
        }
        resClass->set_omit_summary_features(cfg_class.omitsummaryfeatures);
        auto res_class_matching_elements_fields = resClass->get_matching_elements_fields();
        for (const auto & field : cfg_class.fields) {
            std::string field_name = field.name;
            std::string command = field.command;
            std::string source_name = field.source;
            LOG(info, "Reconfiguring class '%s' field '%s'", cfg_class.name.c_str(), field_name.c_str());
            auto factory = [&]() -> std::unique_ptr<DocsumFieldWriter> {
                if (! command.empty()) {
                    try {
                        return docsum_field_writer_factory
                                .create_docsum_field_writer(field_name,
                                                            command,
                                                            source_name);
                    } catch (const vespalib::IllegalArgumentException& ex) {
                        LOG(error, "Exception during setup of summary result class '%s': field='%s', command='%s', source='%s': %s",
                            cfg_class.name.c_str(), field_name.c_str(), command.c_str(), source_name.c_str(), ex.getMessage().c_str());
                        rc = false;
                    }
                }
                return {};
            };
            {
                auto source = field.source.empty() ? field.name : field.source;
                auto elements_selector = make_summary_elements_selector(field.elements, source, struct_fields_mapper);
                auto writer = factory();
                elements_selector.maybe_apply_to(*res_class_matching_elements_fields);
                if (!resClass->addConfigEntry(field_name, elements_selector, std::move(writer))) {
                    LOG(error, "%s %s.fields: duplicate name '%s'", configId.c_str(), cfg_class.name.c_str(), field_name.c_str());
                    rc = false;
                    break;
                }
            }
            if (unionOfAll->getIndexFromName(field_name) < 0) {
                auto source = field.source.empty() ? field.name : field.source;
                auto elements_selector = make_summary_elements_selector(field.elements, source, struct_fields_mapper);
                auto writer = factory();
                elements_selector.maybe_apply_to(*union_of_all_matching_elements_fields);
                unionOfAll->addConfigEntry(field_name, elements_selector, std::move(writer));
            }
        }
    }
    if (!rc) {
        reset();          // FAIL, discard all config
    }
    return rc;
}

}
