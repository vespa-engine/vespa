// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "attribute_reprocessing_initializer.h"
#include <vespa/searchcore/proton/attribute/attribute_populator.h>
#include <vespa/searchcore/proton/attribute/document_field_populator.h>
#include <vespa/searchcore/proton/attribute/filter_attribute_manager.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.reprocessing.attribute_reprocessing_initializer");

using namespace search::index;
using search::AttributeGuard;
using search::AttributeVector;
using search::SerialNum;

namespace proton {

typedef AttributeReprocessingInitializer::Config ARIConfig;

namespace {

constexpr search::SerialNum ATTRIBUTE_INIT_SERIAL = 1;

const char *
toStr(bool value)
{
    return (value ? "true" : "false");
}

bool fastPartialUpdateAttribute(const schema::DataType &attrType) {
    // Partial update to tensor or predicate attribute must update document
    return ((attrType != schema::BOOLEANTREE) && (attrType != schema::TENSOR));
}


FilterAttributeManager::AttributeSet
getAttributeSetToPopulate(const ARIConfig &newCfg,
                          const ARIConfig &oldCfg,
                          const IDocumentTypeInspector &inspector,
                          search::SerialNum serialNum)
{
    FilterAttributeManager::AttributeSet attrsToPopulate;
    std::vector<AttributeGuard> attrList;
    newCfg.getAttrMgr()->getAttributeList(attrList);
    for (const auto &guard : attrList) {
        const vespalib::string &name = guard->getName();
        bool inOldAttrMgr = oldCfg.getAttrMgr()->getAttribute(name)->valid();
        bool unchangedField = inspector.hasUnchangedField(name);
        search::SerialNum flushedSerialNum = newCfg.getAttrMgr()->getFlushedSerialNum(name);
        bool populateAttribute = !inOldAttrMgr && unchangedField && (flushedSerialNum < serialNum);
        LOG(debug, "getAttributeSetToPopulate(): name='%s', inOldAttrMgr=%s, unchangedField=%s, populate=%s",
                name.c_str(), toStr(inOldAttrMgr), toStr(unchangedField), toStr(populateAttribute));
        if (populateAttribute) {
            attrsToPopulate.insert(name);
        }
    }
    return attrsToPopulate;
}

IReprocessingReader::SP
getAttributesToPopulate(const ARIConfig &newCfg,
                        const ARIConfig &oldCfg,
                        const IDocumentTypeInspector &inspector,
                        const vespalib::string &subDbName,
                        search::SerialNum serialNum)
{
    FilterAttributeManager::AttributeSet attrsToPopulate =
        getAttributeSetToPopulate(newCfg, oldCfg, inspector, serialNum);
    if (!attrsToPopulate.empty()) {
        return IReprocessingReader::SP(new AttributePopulator
                (IAttributeManager::SP(new FilterAttributeManager
                        (attrsToPopulate, newCfg.getAttrMgr())),
                        ATTRIBUTE_INIT_SERIAL, subDbName, serialNum));
    }
    return IReprocessingReader::SP();
}

Schema::AttributeField
getAttributeField(const Schema &schema, const vespalib::string &name)
{
    uint32_t attrFieldId = schema.getAttributeFieldId(name);
    assert(attrFieldId != Schema::UNKNOWN_FIELD_ID);
    return schema.getAttributeField(attrFieldId);
}

std::vector<IReprocessingRewriter::SP>
getFieldsToPopulate(const ARIConfig &newCfg,
                    const ARIConfig &oldCfg,
                    const IDocumentTypeInspector &inspector,
                    const vespalib::string &subDbName)
{
    std::vector<IReprocessingRewriter::SP> fieldsToPopulate;
    std::vector<AttributeGuard> attrList;
    oldCfg.getAttrMgr()->getAttributeList(attrList);
    for (const auto &guard : attrList) {
        const vespalib::string &name = guard->getName();
        Schema::AttributeField attrField = getAttributeField(oldCfg.getSchema(), name);
        Schema::DataType attrType(attrField.getDataType());
        bool inNewAttrMgr = newCfg.getAttrMgr()->getAttribute(name)->valid();
        bool unchangedField = inspector.hasUnchangedField(name);
        // NOTE: If it is a string and index field we shall
        // keep the original in order to preserve annotations.
        bool isStringIndexField = attrField.getDataType() == schema::STRING &&
                newCfg.getSchema().isIndexField(name);
        bool populateField = !inNewAttrMgr && unchangedField && !isStringIndexField &&
                             fastPartialUpdateAttribute(attrType);
        LOG(debug, "getFieldsToPopulate(): name='%s', inNewAttrMgr=%s, unchangedField=%s, "
                "isStringIndexField=%s, dataType=%s, populate=%s",
                name.c_str(), toStr(inNewAttrMgr), toStr(unchangedField),
            toStr(isStringIndexField),
            schema::getTypeName(attrType).c_str(),
            toStr(populateField));
        if (populateField) {
            fieldsToPopulate.push_back(IReprocessingRewriter::SP
                    (new DocumentFieldPopulator(attrField,
                            guard.getSP(), subDbName)));
        }
    }
    return fieldsToPopulate;
}

}

AttributeReprocessingInitializer::
AttributeReprocessingInitializer(const Config &newCfg,
                                 const Config &oldCfg,
                                 const IDocumentTypeInspector &inspector,
                                 const vespalib::string &subDbName,
                                 search::SerialNum serialNum)
    : _attrsToPopulate(getAttributesToPopulate(newCfg, oldCfg, inspector, subDbName, serialNum)),
      _fieldsToPopulate(getFieldsToPopulate(newCfg, oldCfg, inspector, subDbName))
{
}

bool
AttributeReprocessingInitializer::hasReprocessors() const
{
    return _attrsToPopulate.get() != nullptr || !_fieldsToPopulate.empty();
}

void
AttributeReprocessingInitializer::initialize(IReprocessingHandler &handler)
{
    if (_attrsToPopulate.get() != nullptr) {
        handler.addReader(_attrsToPopulate);
    }
    if (!_fieldsToPopulate.empty()) {
        for (const auto &rewriter : _fieldsToPopulate) {
            handler.addRewriter(rewriter);
        }
    }
}

} // namespace proton
