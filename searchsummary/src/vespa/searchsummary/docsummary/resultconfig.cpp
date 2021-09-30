// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultconfig.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.resultconfig");

namespace search::docsummary {

void
ResultConfig::Clean()
{
    _classLookup.clear();
    _nameLookup.clear();
}


void
ResultConfig::Init()
{
}


ResultConfig::ResultConfig()
    : _defaultSummaryId(-1),
      _classLookup(),
      _nameLookup()
{
    Init();
}


ResultConfig::~ResultConfig()
{
    Clean();
}


const char *
ResultConfig::GetResTypeName(ResType type)
{
    switch (type) {
    case RES_INT:         return "integer";
    case RES_SHORT:       return "short";
    case RES_BYTE:        return "byte";
    case RES_BOOL:        return "bool";
    case RES_FLOAT:       return "float";
    case RES_DOUBLE:      return "double";
    case RES_INT64:       return "int64";
    case RES_STRING:      return "string";
    case RES_DATA:        return "data";
    case RES_LONG_STRING: return "longstring";
    case RES_LONG_DATA:   return "longdata";
    case RES_XMLSTRING:   return "xmlstring";
    case RES_JSONSTRING:  return "jsonstring";
    case RES_TENSOR:  return "tensor";
    case RES_FEATUREDATA: return "featuredata";
    }
    return "unknown-type";
}

void
ResultConfig::Reset()
{
    if (! _classLookup.empty() || _fieldEnum.GetNumEntries() > 0) {
        Clean();
        Init();
    }
}


ResultClass *
ResultConfig::AddResultClass(const char *name, uint32_t id)
{
    ResultClass *ret = nullptr;

    if (id != NoClassID() && (_classLookup.find(id) == _classLookup.end())) {
        ResultClass::UP rc(new ResultClass(name, id, _fieldEnum));
        ret = rc.get();
        _classLookup[id] = std::move(rc);
        if (_nameLookup.find(name) != _nameLookup.end()) {
            LOG(warning, "Duplicate result class name: %s (now maps to class id %u)", name, id);
        }
        _nameLookup[name] = id;
    }
    return ret;
}


const ResultClass*
ResultConfig::LookupResultClass(uint32_t id) const
{
    IdMap::const_iterator it(_classLookup.find(id));
    return (it != _classLookup.end()) ? it->second.get() : nullptr;
}

uint32_t
ResultConfig::LookupResultClassId(const vespalib::string &name, uint32_t def) const
{
    NameMap::const_iterator found(_nameLookup.find(name));
    return (found != _nameLookup.end()) ? found->second : def;
}

uint32_t
ResultConfig::LookupResultClassId(const vespalib::string &name) const
{
    return LookupResultClassId(name, (name.empty() || (name == "default")) ? _defaultSummaryId : NoClassID());
}


void
ResultConfig::CreateEnumMaps()
{
    for (auto & entry : _classLookup) {
       entry.second->CreateEnumMap();
    }
}


bool
ResultConfig::ReadConfig(const vespa::config::search::SummaryConfig &cfg, const char *configId)
{
    bool rc = true;
    Reset();
    int    maxclassID = 0x7fffffff; // avoid negative classids
    _defaultSummaryId = cfg.defaultsummaryid;
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
            LOG(debug, "Reconfiguring class '%s' field '%s' of type '%s'", cfg_class.name.c_str(), fieldname, fieldtype);
            if (strcmp(fieldtype, "integer") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_INT);
            } else if (strcmp(fieldtype, "short") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_SHORT);
            } else if (strcmp(fieldtype, "bool") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_BOOL);
            } else if (strcmp(fieldtype, "byte") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_BYTE);
            } else if (strcmp(fieldtype, "float") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_FLOAT);
            } else if (strcmp(fieldtype, "double") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_DOUBLE);
            } else if (strcmp(fieldtype, "int64") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_INT64);
            } else if (strcmp(fieldtype, "string") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_STRING);
            } else if (strcmp(fieldtype, "data") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_DATA);
            } else if (strcmp(fieldtype, "raw") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_DATA);
            } else if (strcmp(fieldtype, "longstring") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_LONG_STRING);
            } else if (strcmp(fieldtype, "longdata") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_LONG_DATA);
            } else if (strcmp(fieldtype, "xmlstring") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_XMLSTRING);
            } else if (strcmp(fieldtype, "jsonstring") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_JSONSTRING);
            } else if (strcmp(fieldtype, "tensor") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_TENSOR);
            } else if (strcmp(fieldtype, "featuredata") == 0) {
                rc = resClass->AddConfigEntry(fieldname, RES_FEATUREDATA);
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

uint32_t
ResultConfig::GetClassID(const char *buf, uint32_t buflen)
{
    uint32_t ret = NoClassID();
    uint32_t tmp32;

    if (buflen >= sizeof(tmp32)) {
        memcpy(&tmp32, buf, sizeof(tmp32));
        ret = tmp32;
    }
    return ret;
}


}
