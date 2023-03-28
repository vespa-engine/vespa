// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributemanager.h"
#include "initialized_attributes_result.h"
#include <vespa/searchcore/proton/initializer/initializer_task.h>
#include <vespa/searchcore/proton/common/alloc_strategy.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/config-attributes.h>

namespace searchcorespi::index { struct IThreadService; }

namespace proton {

class DocumentMetaStoreAttribute;

/**
 * Class used to initialize an attribute manager.
 */
class AttributeManagerInitializer : public initializer::InitializerTask
{
private:
    search::SerialNum                       _configSerialNum;
    std::shared_ptr<DocumentMetaStoreAttribute>  _documentMetaStore;
    AttributeManager::SP                    _attrMgr;
    vespa::config::search::AttributesConfig _attrCfg;
    AllocStrategy                           _alloc_strategy;
    bool                                    _fastAccessAttributesOnly;
    searchcorespi::index::IThreadService   &_master;
    InitializedAttributesResult             _attributesResult;
    std::shared_ptr<AttributeManager::SP>   _attrMgrResult;

    std::unique_ptr<AttributeCollectionSpec> createAttributeSpec() const;

public:
    AttributeManagerInitializer(search::SerialNum configSerialNum,
                                initializer::InitializerTask::SP documentMetaStoreInitTask,
                                std::shared_ptr<DocumentMetaStoreAttribute> documentMetaStore,
                                const AttributeManager & baseAttrMgr,
                                const vespa::config::search::AttributesConfig &attrCfg,
                                const AllocStrategy& alloc_strategy,
                                bool fastAccessAttributesOnly,
                                searchcorespi::index::IThreadService &master,
                                std::shared_ptr<AttributeManager::SP> attrMgrResult);
    ~AttributeManagerInitializer() override;

    void run() override;
};

} // namespace proton
