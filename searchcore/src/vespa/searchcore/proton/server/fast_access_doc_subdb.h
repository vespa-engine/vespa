// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fast_access_doc_subdb_configurer.h"
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include "storeonlydocsubdb.h"
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/common/docid_limit.h>

namespace proton {

/**
 * The fast-access sub database keeps fast-access attribute fields in memory
 * in addition to the underlying document store managed by the parent class.
 *
 * Partial updates and document selection on one of these attribute fields will be
 * fast compared to only using the document store.
 * This class is used as base class for the searchable sub database and directly by
 * the "2.notready" sub database for handling not-ready documents.
 * When used by the "2.notready" sub database attributes that are added without any files
 * on disk will be populated based on the content of the document store upon initialization
 * of the sub database.
 */
class FastAccessDocSubDB : public StoreOnlyDocSubDB
{
public:
    struct Config
    {
        const StoreOnlyDocSubDB::Config _storeOnlyCfg;
        const bool                      _hasAttributes;
        const bool                      _addMetrics;
        const bool                      _fastAccessAttributesOnly;
        Config(const StoreOnlyDocSubDB::Config &storeOnlyCfg,
               bool hasAttributes,
               bool addMetrics,
               bool fastAccessAttributesOnly)
        : _storeOnlyCfg(storeOnlyCfg),
          _hasAttributes(hasAttributes),
          _addMetrics(addMetrics),
          _fastAccessAttributesOnly(fastAccessAttributesOnly)
        {
        }
    };

    struct Context
    {
        const StoreOnlyDocSubDB::Context _storeOnlyCtx;
        AttributeMetrics                &_subAttributeMetrics;
        AttributeMetrics                *_totalAttributeMetrics;
        MetricsWireService              &_metricsWireService;
        Context(const StoreOnlyDocSubDB::Context &storeOnlyCtx,
                AttributeMetrics &subAttributeMetrics,
                AttributeMetrics *totalAttributeMetrics,
                MetricsWireService &metricsWireService)
        : _storeOnlyCtx(storeOnlyCtx),
          _subAttributeMetrics(subAttributeMetrics),
          _totalAttributeMetrics(totalAttributeMetrics),
          _metricsWireService(metricsWireService)
        {
        }
    };

private:
    typedef vespa::config::search::AttributesConfig AttributesConfig;
    typedef FastAccessDocSubDBConfigurer Configurer;

    const bool                    _hasAttributes;
    const bool                    _fastAccessAttributesOnly;
    AttributeManager::SP          _initAttrMgr;
    Configurer::FeedViewVarHolder _fastUpdateFeedView;
    AttributeMetrics             &_subAttributeMetrics;
    AttributeMetrics             *_totalAttributeMetrics;

    initializer::InitializerTask::SP
    createAttributeManagerInitializer(const DocumentDBConfig &configSnapshot,
                                      SerialNum configSerialNum,
                                      initializer::InitializerTask::SP documentMetaStoreInitTask,
                                      DocumentMetaStore::SP documentMetaStore,
                                      std::shared_ptr<AttributeManager::SP> attrMgrResult) const;

    void setupAttributeManager(AttributeManager::SP attrMgrResult);

    void initFeedView(const IAttributeWriter::SP &writer,
                      const DocumentDBConfig &configSnapshot);


protected:
    typedef StoreOnlyDocSubDB Parent;
    typedef vespa::config::search::core::ProtonConfig ProtonConfig;

    const bool           _addMetrics;
    MetricsWireService  &_metricsWireService;
    DocIdLimit           _docIdLimit;

    AttributeCollectionSpec::UP createAttributeSpec(const AttributesConfig &attrCfg,
                                                    SerialNum serialNum) const;

    AttributeManager::SP getAndResetInitAttributeManager();

    virtual IFlushTarget::List getFlushTargetsInternal();

    void reconfigureAttributeMetrics(const proton::IAttributeManager &newMgr,
                                     const proton::IAttributeManager &oldMgr);

    IReprocessingTask::UP
    createReprocessingTask(IReprocessingInitializer &initializer,
                           const document::DocumentTypeRepo::SP &docTypeRepo) const;

public:
    FastAccessDocSubDB(const Config &cfg,
                       const Context &ctx);

    virtual ~FastAccessDocSubDB() {}

    virtual DocumentSubDbInitializer::UP
    createInitializer(const DocumentDBConfig &configSnapshot,
                      SerialNum configSerialNum,
                      const search::index::Schema::SP &unionSchema,
                      const vespa::config::search::core::ProtonConfig::Summary &protonSummaryCfg,
                      const vespa::config::search::core::ProtonConfig::Index &indexCfg) const override;

    virtual void setup(const DocumentSubDbInitializerResult &initResult) override;

    virtual void initViews(const DocumentDBConfig &configSnapshot,
                           const proton::matching::SessionManager::SP &sessionManager);

    virtual IReprocessingTask::List applyConfig(const DocumentDBConfig &newConfigSnapshot,
                                                const DocumentDBConfig &oldConfigSnapshot,
                                                SerialNum serialNum,
                                                const ReconfigParams params);

    virtual proton::IAttributeManager::SP getAttributeManager() const;

    virtual IDocumentRetriever::UP getDocumentRetriever();

    virtual void
    onReplayDone();

    virtual void
    onReprocessDone(SerialNum serialNum);

    virtual SerialNum
    getOldestFlushedSerial();

    virtual SerialNum
    getNewestFlushedSerial();
};

} // namespace proton

