// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_combiner_dfw.h"
#include "array_attribute_combiner_dfw.h"
#include "docsum_field_writer_state.h"
#include "docsumstate.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".searchsummary.docsummary.attribute_combiner_dfw");

using search::AttributeGuard;
using search::AttributeVector;
using search::attribute::CollectionType;

namespace search::docsummary {

AttributeCombinerDFW::AttributeCombinerDFW(const vespalib::string &fieldName)
    : IDocsumFieldWriter(),
      _stateIndex(0),
      _fieldName(fieldName)
{
}

AttributeCombinerDFW::~AttributeCombinerDFW() = default;

bool
AttributeCombinerDFW::IsGenerated() const
{
    return true;
}

bool
AttributeCombinerDFW::setFieldWriterStateIndex(uint32_t fieldWriterStateIndex)
{
    _stateIndex = fieldWriterStateIndex;
    return true;
}

std::unique_ptr<IDocsumFieldWriter>
AttributeCombinerDFW::create(const vespalib::string &fieldName, IAttributeManager &attrMgr)
{
    // Note: Doesn't handle imported attributes
    std::vector<AttributeGuard> attrs;
    attrMgr.getAttributeList(attrs);
    vespalib::string prefix = fieldName + ".";
    vespalib::string keyName = prefix + "key";
    vespalib::string valuePrefix = prefix + "value.";
    std::vector<vespalib::string> mapFields;
    std::vector<vespalib::string> arrayFields;
    bool foundKey = false;
    for (const auto &guard : attrs) {
        vespalib::string name = guard->getName();
        if (name.substr(0, prefix.size()) != prefix) {
            continue;
        }
        auto collType = guard->getCollectionType();
        if (collType != CollectionType::Type::ARRAY) {
            LOG(warning, "Attribute %s is not an array attribute", name.c_str());
            return std::unique_ptr<IDocsumFieldWriter>();
        }
        if (name.substr(0, valuePrefix.size()) == valuePrefix) {
            mapFields.emplace_back(name.substr(valuePrefix.size()));
        } else {
            arrayFields.emplace_back(name.substr(prefix.size()));
            if (name == keyName) {
                foundKey = true;
            }
        }
    }
    if (!mapFields.empty()) {
        if (!foundKey) {
            LOG(warning, "Missing key attribute '%s', have value attributes for map", keyName.c_str());
            return std::unique_ptr<IDocsumFieldWriter>();
        }
        if (arrayFields.size() != 1u) {
            LOG(warning, "Could not determine if field '%s' is array or map of struct", fieldName.c_str());
            return std::unique_ptr<IDocsumFieldWriter>();
        }
        LOG(warning, "map of struct is not yet supported for field '%s'", fieldName.c_str());
        return std::unique_ptr<IDocsumFieldWriter>();
    }
    std::sort(arrayFields.begin(), arrayFields.end());
    return std::make_unique<ArrayAttributeCombinerDFW>(fieldName, arrayFields);
}

void
AttributeCombinerDFW::insertField(uint32_t docid,
                                  GeneralResult *,
                                  GetDocsumsState *state,
                                  ResType,
                                  vespalib::slime::Inserter &target)
{
    auto &fieldWriterState = state->_fieldWriterStates[_stateIndex];
    if (!fieldWriterState) {
        fieldWriterState = allocFieldWriterState(*state->_attrCtx);
    }
    fieldWriterState->insertField(docid, target);
}

}

