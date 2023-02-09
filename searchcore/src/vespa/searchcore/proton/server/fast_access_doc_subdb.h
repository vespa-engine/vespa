// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "storeonlydocsubdb.h"
#include "fast_access_doc_subdb_configurer.h"
#include <vespa/config-attributes.h>
#include <vespa/searchcore/proton/common/docid_limit.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>

namespace search::attribute { class Interlock; }

namespace proton {

class AttributeManager;

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
        AttributeMetrics                &_subAttributeMetrics;
        MetricsWireService              &_metricsWireService;
        std::shared_ptr<search::attribute::Interlock> _attribute_interlock;
        Context(const StoreOnlyDocSubDB::Context &storeOnlyCtx,
                AttributeMetrics &subAttributeMetrics,
                MetricsWireService &metricsWireService,
                std::shared_ptr<search::attribute::Interlock> attribute_interlock)
        : _storeOnlyCtx(storeOnlyCtx),
          _subAttributeMetrics(subAttributeMetrics),
          _metricsWireService(metricsWireService),
          _attribute_interlock(std::move(attribute_interlock))
        { }
        ~Context();
    };

private:
    using AttributesConfig = vespa::config::search::AttributesConfig;
    using Configurer = FastAccessDocSubDBConfigurer;

    const bool                    _hasAttributes;
    const bool                    _fastAccessAttributesOnly;
    std::shared_ptr<AttributeManager> _initAttrMgr;
    Configurer::FeedViewVarHolder _fastAccessFeedView;
    Configurer                    _configurer;
    AttributeMetrics             &_subAttributeMetrics;

    std::shared_ptr<initializer::InitializerTask>
    createAttributeManagerInitializer(const DocumentDBConfig &configSnapshot,
                                      SerialNum configSerialNum,
                                      std::shared_ptr<initializer::InitializerTask> documentMetaStoreInitTask,
                                      DocumentMetaStore::SP documentMetaStore,
                                      std::shared_ptr<std::shared_ptr<AttributeManager>> attrMgrResult) const;

    void setupAttributeManager(std::shared_ptr<AttributeManager> attrMgrResult);
    void initFeedView(std::shared_ptr<IAttributeWriter> writer, const DocumentDBConfig &configSnapshot);

protected:
    using Parent = StoreOnlyDocSubDB;

    const bool           _addMetrics;
    MetricsWireService  &_metricsWireService;
    std::shared_ptr<search::attribute::Interlock> _attribute_interlock;
    DocIdLimit           _docIdLimit;

    std::shared_ptr<AttributeManager> getAndResetInitAttributeManager();
    virtual IFlushTargetList getFlushTargetsInternal() override;
    void reconfigureAttributeMetrics(const IAttributeManager &newMgr, const IAttributeManager &oldMgr);

    IReprocessingTask::UP createReprocessingTask(IReprocessingInitializer &initializer,
                                                 const std::shared_ptr<const document::DocumentTypeRepo> &docTypeRepo) const;

    bool has_fast_access_attributes_only() const noexcept { return _fastAccessAttributesOnly; }

public:
    FastAccessDocSubDB(const Config &cfg, const Context &ctx);
    ~FastAccessDocSubDB();

    std::unique_ptr<DocumentSubDbInitializer>
    createInitializer(const DocumentDBConfig &configSnapshot, SerialNum configSerialNum,
                      const IndexConfig &indexCfg) const override;

    void setup(const DocumentSubDbInitializerResult &initResult) override;
    void initViews(const DocumentDBConfig &configSnapshot) override;

    std::unique_ptr<DocumentSubDBReconfig>
    prepare_reconfig(const DocumentDBConfig& new_config_snapshot, const ReconfigParams& reconfig_params, std::optional<SerialNum> serial_num) override;
    IReprocessingTask::List
    applyConfig(const DocumentDBConfig &newConfigSnapshot, const DocumentDBConfig &oldConfigSnapshot,
                SerialNum serialNum, const ReconfigParams &params, IDocumentDBReferenceResolver &resolver, const DocumentSubDBReconfig& prepared_reconfig) override;

    std::shared_ptr<IAttributeManager> getAttributeManager() const override;
    IDocumentRetriever::UP getDocumentRetriever() override;
    void onReplayDone() override;
    void onReprocessDone(SerialNum serialNum) override;
    SerialNum getOldestFlushedSerial() override;
    SerialNum getNewestFlushedSerial() override;
    virtual void pruneRemovedFields(SerialNum serialNum) override;
    TransientResourceUsage get_transient_resource_usage() const override;
};

} // namespace proton
