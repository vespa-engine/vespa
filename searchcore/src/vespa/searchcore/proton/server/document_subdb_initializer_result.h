// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/document_meta_store_initializer_result.h>
#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/searchcore/proton/documentmetastore/lid_reuse_delayer_config.h>

namespace proton {

/**
 * The result after initializing components used by a document sub database.
 *
 * The document sub database takes ownership of these (initialized) components.
 */
class DocumentSubDbInitializerResult
{
private:
    std::shared_ptr<DocumentMetaStoreInitializerResult::SP> _documentMetaStore;
    std::shared_ptr<SummaryManager::SP> _summaryManager;
    std::shared_ptr<AttributeManager::SP> _attributeManager;
    std::shared_ptr<searchcorespi::IIndexManager::SP> _indexManager;
    using LidReuseDelayerConfig = documentmetastore::LidReuseDelayerConfig;
    LidReuseDelayerConfig _lidReuseDelayerConfig;

public:
    DocumentSubDbInitializerResult();

    std::shared_ptr<DocumentMetaStoreInitializerResult::SP>
    writableDocumentMetaStore() { return _documentMetaStore; }
    DocumentMetaStoreInitializerResult::SP documentMetaStore() const {
        return *_documentMetaStore;
    }
    std::shared_ptr<SummaryManager::SP> writableSummaryManager() {
        return _summaryManager;
    }
    SummaryManager::SP summaryManager() const {
        return *_summaryManager;
    }
    std::shared_ptr<AttributeManager::SP> writableAttributeManager() {
        return _attributeManager;
    }
    AttributeManager::SP attributeManager() const {
        return *_attributeManager;
    }
    std::shared_ptr<searchcorespi::IIndexManager::SP> writableIndexManager() {
        return _indexManager;
    }
    searchcorespi::IIndexManager::SP indexManager() const {
        return *_indexManager;
    }
    void setLidReuseDelayerConfig(LidReuseDelayerConfig
                                  lidReuseDelayerConfig_in);
    const LidReuseDelayerConfig &lidReuseDelayerConfig() const {
        return _lidReuseDelayerConfig;
    }
};

} // namespace proton

