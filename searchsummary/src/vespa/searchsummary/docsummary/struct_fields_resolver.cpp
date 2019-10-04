// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "struct_fields_resolver.h"
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".searchsummary.docsummary.struct_fields_resolver");

using search::attribute::CollectionType;

namespace search::docsummary {

StructFieldsResolver::StructFieldsResolver(const vespalib::string& fieldName, const IAttributeManager& attrMgr)
    : _mapFields(),
      _arrayFields(),
      _hasMapKey(false),
      _error(false)
{
    std::vector<const search::attribute::IAttributeVector *> attrs;
    auto attrCtx = attrMgr.createContext();
    attrCtx->getAttributeList(attrs);
    vespalib::string prefix = fieldName + ".";
    vespalib::string keyName = prefix + "key";
    vespalib::string valuePrefix = prefix + "value.";
    for (const auto attr : attrs) {
        vespalib::string name = attr->getName();
        if (name.substr(0, prefix.size()) != prefix) {
            continue;
        }
        auto collType = attr->getCollectionType();
        if (collType != CollectionType::Type::ARRAY) {
            LOG(warning, "Attribute %s is not an array attribute", name.c_str());
            _error = true;
            break;
        }
        if (name.substr(0, valuePrefix.size()) == valuePrefix) {
            _mapFields.emplace_back(name.substr(valuePrefix.size()));
        } else {
            _arrayFields.emplace_back(name.substr(prefix.size()));
            if (name == keyName) {
                _hasMapKey = true;
            }
        }
    }
    if (!_error) {
        std::sort(_arrayFields.begin(), _arrayFields.end());
        std::sort(_mapFields.begin(), _mapFields.end());
        if (!_mapFields.empty()) {
            if (!_hasMapKey) {
                LOG(warning, "Missing key attribute '%s', have value attributes for map", keyName.c_str());
                _error = true;
            } else if (_arrayFields.size() != 1u) {
                LOG(warning, "Could not determine if field '%s' is array or map of struct", fieldName.c_str());
                _error = true;
            }
        }
    }
}

StructFieldsResolver::~StructFieldsResolver() = default;

}

