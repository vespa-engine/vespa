// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isummarymanager.h"
#include "fieldcacherepo.h"
#include <vespa/searchcore/config/config-proton.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/docstore/idatastore.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/fastlib/text/normwordfolder.h>

namespace search {

class IBucketizer;

namespace common { class FileHeaderContext; }

}

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
        const document::DocumentTypeRepo::SP  _repo;
        std::set<vespalib::string>            _markupFields;
    public:
        SummarySetup(const vespalib::string & baseDir,
                     const DocTypeName & docTypeName,
                     const vespa::config::search::SummaryConfig & summaryCfg,
                     const vespa::config::search::SummarymapConfig & summarymapCfg,
                     const vespa::config::search::summary::JuniperrcConfig & juniperCfg,
                     const search::IAttributeManager::SP &attributeMgr,
                     const search::IDocumentStore::SP & docStore,
                     const document::DocumentTypeRepo::SP &repo);

        /**
         * Implements ISummarySetup.
         */
        search::docsummary::IDocsumWriter & getDocsumWriter() const { return *_docsumWriter; }
        search::docsummary::ResultConfig & getResultConfig() { return *_docsumWriter->GetResultConfig(); }

        search::docsummary::IDocsumStore::UP createDocsumStore(
                const vespalib::string &resultClassName);

        // Inherit doc from IDocsumEnvironment
        virtual search::IAttributeManager * getAttributeManager() { return _attributeMgr.get(); }
        virtual vespalib::string lookupIndex(const vespalib::string & s) const { (void) s; return ""; }
        virtual juniper::Juniper * getJuniper() { return _juniperConfig.get(); }
    };

private:
    vespalib::string               _baseDir;
    DocTypeName                    _docTypeName;
    search::IDocumentStore::SP     _docStore;
    const search::TuneFileSummary  _tuneFileSummary;
    uint64_t                       _currentSerial;

public:
    typedef std::shared_ptr<SummaryManager> SP;
    SummaryManager(vespalib::ThreadExecutor & executor,
                   const vespa::config::search::core::ProtonConfig::Summary & summary,
                   const search::GrowStrategy & growStrategy,
                   const vespalib::string &baseDir,
                   const DocTypeName &docTypeName,
                   const search::TuneFileSummary &tuneFileSummary,
                   const search::common::FileHeaderContext &fileHeaderContext,
                   search::transactionlog::SyncProxy &tlSyncer,
                   const std::shared_ptr<search::IBucketizer> & bucketizer);

    void putDocument(uint64_t syncToken, const document::Document & doc,
                     search::DocumentIdT lid);
    void removeDocument(uint64_t syncToken, search::DocumentIdT lid);
    searchcorespi::IFlushTarget::List getFlushTargets();

    /**
     * Implements ISummaryManager.
     */
    virtual ISummarySetup::SP
    createSummarySetup(const vespa::config::search::SummaryConfig &summaryCfg,
                       const vespa::config::search::SummarymapConfig &summarymapCfg,
                       const vespa::config::search::summary::JuniperrcConfig &juniperCfg,
                       const document::DocumentTypeRepo::SP &repo,
                       const search::IAttributeManager::SP &attributeMgr);

    virtual search::IDocumentStore & getBackingStore() { return *_docStore; }

};

} // namespace proton

