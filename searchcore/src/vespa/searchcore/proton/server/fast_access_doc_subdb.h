// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "fast_access_doc_subdb_configurer.h"
#include "storeonlydocsubdb.h"
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/common/docid_limit.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics_collection.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>

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
        { }
    };

    struct Context
    {
        const StoreOnlyDocSubDB::Context _storeOnlyCtx;
        const AttributeMetricsCollection &_subAttributeMetrics;
        LegacyAttributeMetrics          *_totalAttributeMetrics;
        MetricsWireService              &_metricsWireService;
        Context(const StoreOnlyDocSubDB::Context &storeOnlyCtx,
                const AttributeMetricsCollection &subAttributeMetrics,
                LegacyAttributeMetrics *totalAttributeMetrics,
                MetricsWireService &metricsWireService)
        : _storeOnlyCtx(storeOnlyCtx),
          _subAttributeMetrics(subAttributeMetrics),
          _totalAttributeMetrics(totalAttributeMetrics),
          _metricsWireService(metricsWireService)
        { }
    };

private:
    using AttributesConfig = vespa::config::search::AttributesConfig;
    using Configurer = FastAccessDocSubDBConfigurer;

    const bool                    _hasAttributes;
    const bool                    _fastAccessAttributesOnly;
    AttributeManager::SP          _initAttrMgr;
    Configurer::FeedViewVarHolder _fastAccessFeedView;
    AttributeMetricsCollection    _subAttributeMetrics;
    LegacyAttributeMetrics       *_totalAttributeMetrics;

    std::shared_ptr<initializer::InitializerTask>
    createAttributeManagerInitializer(const DocumentDBConfig &configSnapshot,
                                      SerialNum configSerialNum,
                                      std::shared_ptr<initializer::InitializerTask> documentMetaStoreInitTask,
                                      DocumentMetaStore::SP documentMetaStore,
                                      std::shared_ptr<AttributeManager::SP> attrMgrResult) const;

    void setupAttributeManager(AttributeManager::SP attrMgrResult);
    void initFeedView(const IAttributeWriter::SP &writer, const DocumentDBConfig &configSnapshot);

protected:
    using Parent = StoreOnlyDocSubDB;
    using SessionManagerSP = std::shared_ptr<matching::SessionManager>;

    const bool           _addMetrics;
    MetricsWireService  &_metricsWireService;
    DocIdLimit           _docIdLimit;

    AttributeCollectionSpec::UP createAttributeSpec(const AttributesConfig &attrCfg, SerialNum serialNum) const;
    AttributeManager::SP getAndResetInitAttributeManager();
    virtual IFlushTargetList getFlushTargetsInternal() override;
    void reconfigureAttributeMetrics(const IAttributeManager &newMgr, const IAttributeManager &oldMgr);

    IReprocessingTask::UP createReprocessingTask(IReprocessingInitializer &initializer,
                                                 const std::shared_ptr<const document::DocumentTypeRepo> &docTypeRepo) const;

public:
    FastAccessDocSubDB(const Config &cfg, const Context &ctx);
    ~FastAccessDocSubDB();

    std::unique_ptr<DocumentSubDbInitializer>
    createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                      const ProtonConfig::Index &indexCfg) const override;

    void setup(const DocumentSubDbInitializerResult &initResult) override;
    void initViews(const DocumentDBConfig &configSnapshot, const SessionManagerSP &sessionManager) override;

    IReprocessingTask::List
    applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver) override;

    proton::IAttributeManager::SP getAttributeManager() const override;
    IDocumentRetriever::UP getDocumentRetriever() override;
    void onReplayDone() override;
    void onReprocessDone(SerialNum serialNum) override;
    SerialNum getOldestFlushedSerial() override;
    SerialNum getNewestFlushedSerial() override;
    virtual void pruneRemovedFields(SerialNum serialNum) override;
};

} // namespace proton
