// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_reprocessing_initializer.h"
#include <vespa/searchcore/proton/attribute/attribute_populator.h>
#include <vespa/searchcommon/attribute/attribute_utils.h>
#include <vespa/searchcore/proton/attribute/document_field_populator.h>
#include <vespa/searchcore/proton/attribute/filter_attribute_manager.h>
#include <vespa/searchcore/proton/common/i_indexschema_inspector.h>
#include <vespa/searchlib/attribute/attributevector.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.reprocessing.attribute_reprocessing_initializer");

using namespace search::index;
using search::attribute::isUpdateableInMemoryOnly;
using search::AttributeGuard;
using search::AttributeVector;
using search::SerialNum;
using search::attribute::BasicType;
using search::index::schema::DataType;

namespace proton {

typedef AttributeReprocessingInitializer::Config ARIConfig;

namespace {

constexpr search::SerialNum ATTRIBUTE_INIT_SERIAL = 1;

const char *
toStr(bool value)
{
    return (value ? "true" : "false");
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
        return std::make_shared<AttributePopulator>
                (std::make_shared<FilterAttributeManager>(attrsToPopulate, newCfg.getAttrMgr()),
                 ATTRIBUTE_INIT_SERIAL, subDbName, serialNum);
    }
    return IReprocessingReader::SP();
}

std::vector<IReprocessingRewriter::SP>
getFieldsToPopulate(const ARIConfig &newCfg,
                    const ARIConfig &oldCfg,
                    const IDocumentTypeInspector &inspector,
                    const IIndexschemaInspector &oldIndexschemaInspector,
                    const vespalib::string &subDbName)
{
    std::vector<IReprocessingRewriter::SP> fieldsToPopulate;
    std::vector<AttributeGuard> attrList;
    oldCfg.getAttrMgr()->getAttributeList(attrList);
    for (const auto &guard : attrList) {
        const vespalib::string &name = guard->getName();
        const auto &attrCfg = guard->getConfig();
        bool inNewAttrMgr = newCfg.getAttrMgr()->getAttribute(name)->valid();
        bool unchangedField = inspector.hasUnchangedField(name);
        // NOTE: If it is a string and index field we shall
        // keep the original in order to preserve annotations.
        bool wasStringIndexField = oldIndexschemaInspector.isStringIndex(name);
        bool populateField = !inNewAttrMgr && unchangedField && !wasStringIndexField &&
                             isUpdateableInMemoryOnly(name, attrCfg);
        LOG(debug, "getFieldsToPopulate(): name='%s', inNewAttrMgr=%s, unchangedField=%s, "
                "wasStringIndexField=%s, dataType=%s, populate=%s",
                name.c_str(), toStr(inNewAttrMgr), toStr(unchangedField),
            toStr(wasStringIndexField),
            attrCfg.basicType().asString(),
            toStr(populateField));
        if (populateField) {
            fieldsToPopulate.push_back(std::make_shared<DocumentFieldPopulator>
                                               (name, guard.getSP(), subDbName));
        }
    }
    return fieldsToPopulate;
}

}

AttributeReprocessingInitializer::
AttributeReprocessingInitializer(const Config &newCfg,
                                 const Config &oldCfg,
                                 const IDocumentTypeInspector &inspector,
                                 const IIndexschemaInspector &oldIndexschemaInspector,
                                 const vespalib::string &subDbName,
                                 search::SerialNum serialNum)
    : _attrsToPopulate(getAttributesToPopulate(newCfg, oldCfg, inspector, subDbName, serialNum)),
      _fieldsToPopulate(getFieldsToPopulate(newCfg, oldCfg, inspector, oldIndexschemaInspector, subDbName))
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
