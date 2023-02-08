// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isummarymanager.h"
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/docstore/logdocumentstore.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/document/fieldvalue/document.h>

namespace search { class IBucketizer; }
namespace search::common { class FileHeaderContext; }

class Fast_NormalizeWordFolder;

namespace proton {

class SummaryManager : public ISummaryManager
{
public:
    class SummarySetup : public ISummarySetup {
    private:
        std::unique_ptr<search::docsummary::DynamicDocsumWriter> _docsumWriter;
        std::unique_ptr<Fast_NormalizeWordFolder>                _wordFolder;
        search::docsummary::JuniperProperties _juniperProps;
        std::unique_ptr<juniper::Juniper>     _juniperConfig;
        search::IAttributeManager::SP         _attributeMgr;
        search::IDocumentStore::SP            _docStore;
        const std::shared_ptr<const document::DocumentTypeRepo>  _repo;
    public:
        SummarySetup(const vespalib::string & baseDir,
                     const SummaryConfig & summaryCfg,
                     const JuniperrcConfig & juniperCfg,
                     search::IAttributeManager::SP attributeMgr,
                     search::IDocumentStore::SP docStore,
                     std::shared_ptr<const document::DocumentTypeRepo> repo,
                     const search::index::Schema& schema);

        search::docsummary::IDocsumWriter & getDocsumWriter() const override { return *_docsumWriter; }
        const search::docsummary::ResultConfig & getResultConfig() override { return *_docsumWriter->GetResultConfig(); }

        search::docsummary::IDocsumStore::UP createDocsumStore() override;

        const search::IAttributeManager * getAttributeManager() const override { return _attributeMgr.get(); }
        const juniper::Juniper * getJuniper() const override { return _juniperConfig.get(); }
    };

private:
    vespalib::string               _baseDir;
    std::shared_ptr<search::IDocumentStore> _docStore;

public:
    using SP = std::shared_ptr<SummaryManager>;
    SummaryManager(vespalib::Executor &shared_executor,
                   const search::LogDocumentStore::Config & summary,
                   const search::GrowStrategy & growStrategy,
                   const vespalib::string &baseDir,
                   const search::TuneFileSummary &tuneFileSummary,
                   const search::common::FileHeaderContext &fileHeaderContext,
                   search::transactionlog::SyncProxy &tlSyncer,
                   std::shared_ptr<search::IBucketizer> bucketizer);
    ~SummaryManager() override;

    void putDocument(uint64_t syncToken, search::DocumentIdT lid, const document::Document & doc);
    void putDocument(uint64_t syncToken, search::DocumentIdT lid, const vespalib::nbostream & doc);
    void removeDocument(uint64_t syncToken, search::DocumentIdT lid);
    searchcorespi::IFlushTarget::List getFlushTargets(vespalib::Executor & summaryService);

    ISummarySetup::SP
    createSummarySetup(const SummaryConfig &summaryCfg,
                       const JuniperrcConfig &juniperCfg,
                       const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                       const search::IAttributeManager::SP &attributeMgr,
                       const search::index::Schema& schema) override;

    search::IDocumentStore & getBackingStore() override { return *_docStore; }
    void reconfigure(const search::LogDocumentStore::Config & config);
};

} // namespace proton

