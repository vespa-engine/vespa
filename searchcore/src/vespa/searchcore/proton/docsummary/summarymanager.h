// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isummarymanager.h"
#include "fieldcacherepo.h"
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/docstore/logdocumentstore.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/fastlib/text/normwordfolder.h>

namespace searchcorespi::index { class IThreadService; }
namespace search { class IBucketizer; }
namespace search::common { class FileHeaderContext; }

namespace proton {

class SummaryManager : public ISummaryManager
{
public:
    class SummarySetup : public ISummarySetup {
    private:
        std::unique_ptr<search::docsummary::DynamicDocsumWriter> _docsumWriter;
        Fast_NormalizeWordFolder              _wordFolder;
        search::docsummary::JuniperProperties _juniperProps;
        std::unique_ptr<juniper::Juniper>     _juniperConfig;
        search::IAttributeManager::SP         _attributeMgr;
        search::IDocumentStore::SP            _docStore;
        FieldCacheRepo::UP                    _fieldCacheRepo;
        const std::shared_ptr<const document::DocumentTypeRepo>  _repo;
        std::set<vespalib::string>            _markupFields;
    public:
        SummarySetup(const vespalib::string & baseDir,
                     const DocTypeName & docTypeName,
                     const vespa::config::search::SummaryConfig & summaryCfg,
                     const vespa::config::search::SummarymapConfig & summarymapCfg,
                     const vespa::config::search::summary::JuniperrcConfig & juniperCfg,
                     const search::IAttributeManager::SP &attributeMgr,
                     const search::IDocumentStore::SP & docStore,
                     const std::shared_ptr<const document::DocumentTypeRepo> &repo);

        search::docsummary::IDocsumWriter & getDocsumWriter() const override { return *_docsumWriter; }
        search::docsummary::ResultConfig & getResultConfig() override { return *_docsumWriter->GetResultConfig(); }

        search::docsummary::IDocsumStore::UP createDocsumStore(const vespalib::string &resultClassName) override;

        search::IAttributeManager * getAttributeManager() override { return _attributeMgr.get(); }
        vespalib::string lookupIndex(const vespalib::string & s) const override { (void) s; return ""; }
        juniper::Juniper * getJuniper() override { return _juniperConfig.get(); }
    };

private:
    vespalib::string               _baseDir;
    DocTypeName                    _docTypeName;
    std::shared_ptr<search::IDocumentStore> _docStore;
    const search::TuneFileSummary  _tuneFileSummary;
    uint64_t                       _currentSerial;

public:
    typedef std::shared_ptr<SummaryManager> SP;
    SummaryManager(vespalib::ThreadExecutor & executor,
                   const search::LogDocumentStore::Config & summary,
                   const search::GrowStrategy & growStrategy,
                   const vespalib::string &baseDir,
                   const DocTypeName &docTypeName,
                   const search::TuneFileSummary &tuneFileSummary,
                   const search::common::FileHeaderContext &fileHeaderContext,
                   search::transactionlog::SyncProxy &tlSyncer,
                   const std::shared_ptr<search::IBucketizer> & bucketizer);
    ~SummaryManager();

    void putDocument(uint64_t syncToken, search::DocumentIdT lid, const document::Document & doc);
    void putDocument(uint64_t syncToken, search::DocumentIdT lid, const vespalib::nbostream & doc);
    void removeDocument(uint64_t syncToken, search::DocumentIdT lid);
    searchcorespi::IFlushTarget::List getFlushTargets(searchcorespi::index::IThreadService & summaryService);

    ISummarySetup::SP
    createSummarySetup(const vespa::config::search::SummaryConfig &summaryCfg,
                       const vespa::config::search::SummarymapConfig &summarymapCfg,
                       const vespa::config::search::summary::JuniperrcConfig &juniperCfg,
                       const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                       const search::IAttributeManager::SP &attributeMgr) override;

    search::IDocumentStore & getBackingStore() override { return *_docStore; }
    void reconfigure(const search::LogDocumentStore::Config & config);
};

} // namespace proton

