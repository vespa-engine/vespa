// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_reprocessing_handler.h"
#include "i_reprocessing_initializer.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/common/i_document_type_inspector.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

class IIndexschemaInspector;

/**
 * Class responsible for initialize reprocessing of attribute vectors if needed.
 *
 * 1) Attribute aspect is added to an existing field:
 *    The attribute is populated based on the content of the field in the document store.
 *
 * 2) Attribute aspect is removed from an existing field:
 *    The field in the document store is populated based on the content of the attribute.
 */
class AttributeReprocessingInitializer : public IReprocessingInitializer
{
public:
    typedef std::unique_ptr<AttributeReprocessingInitializer> UP;

    class Config
    {
    private:
        proton::IAttributeManager::SP _attrMgr;
        const search::index::Schema &_schema;
    public:
        Config(const proton::IAttributeManager::SP &attrMgr,
               const search::index::Schema &schema)
            : _attrMgr(attrMgr),
              _schema(schema)
        {
        }
        const proton::IAttributeManager::SP &getAttrMgr() const { return _attrMgr; }
        const search::index::Schema &getSchema() const { return _schema; }
    };

private:
    IReprocessingReader::SP                _attrsToPopulate;
    std::vector<IReprocessingRewriter::SP> _fieldsToPopulate;

public:
    AttributeReprocessingInitializer(const Config &newCfg,
                                     const Config &oldCfg,
                                     const IDocumentTypeInspector &inspector,
                                     const IIndexschemaInspector &oldIndexschemaInspector,
                                     const vespalib::string &subDbName,
                                     search::SerialNum serialNum);

    // Implements IReprocessingInitializer
    virtual bool hasReprocessors() const override;

    virtual void initialize(IReprocessingHandler &handler) override;
};

} // namespace proton

