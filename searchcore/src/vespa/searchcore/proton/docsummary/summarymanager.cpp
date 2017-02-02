// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentstoreadapter.h"
#include "summarycompacttarget.h"
#include "summaryflushtarget.h"
#include "summarymanager.h"
#include <vespa/searchlib/docstore/logdocumentstore.h>
#include <vespa/searchsummary/docsummary/docsumconfig.h>
#include <vespa/config/print/ostreamconfigwriter.h>
#include <vespa/juniper/rpinterface.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.summarymanager");

using namespace config;
using namespace document;
using namespace search::docsummary;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using vespalib::make_string;
using vespalib::IllegalArgumentException;
using search::DocumentStore;
using search::LogDocumentStore;
using search::LogDataStore;
using search::WriteableFileChunk;

using search::TuneFileSummary;
using search::common::FileHeaderContext;

namespace proton {

SummaryManager::SummarySetup::
SummarySetup(const vespalib::string & baseDir,
             const DocTypeName & docTypeName,
             const SummaryConfig & summaryCfg,
             const SummarymapConfig & summarymapCfg,
             const JuniperrcConfig & juniperCfg,
             const search::IAttributeManager::SP &attributeMgr,
             const search::IDocumentStore::SP & docStore,
             const DocumentTypeRepo::SP &repo)
    : _docsumWriter(),
      _wordFolder(),
      _juniperProps(juniperCfg),
      _juniperConfig(),
      _attributeMgr(attributeMgr),
      _docStore(docStore),
      _fieldCacheRepo(),
      _repo(repo),
      _markupFields()
{
    std::unique_ptr<ResultConfig> resultConfig(new ResultConfig());
    if (!resultConfig->ReadConfig(summaryCfg, make_string("SummaryManager(%s)", baseDir.c_str()).c_str())) {
        std::ostringstream oss;
        config::OstreamConfigWriter writer(oss);
        writer.write(summaryCfg);
        throw IllegalArgumentException
            (make_string("Could not initialize summary result config for directory '%s' based on summary config '%s'",
                         baseDir.c_str(), oss.str().c_str()));
    }

    _juniperConfig.reset(new juniper::Juniper(&_juniperProps, &_wordFolder));
    _docsumWriter.reset(new DynamicDocsumWriter(resultConfig.release(), NULL));
    DynamicDocsumConfig dynCfg(this, _docsumWriter.get());
    dynCfg.configure(summarymapCfg);
    for (size_t i = 0; i < summarymapCfg.override.size(); ++i) {
        const SummarymapConfig::Override & o = summarymapCfg.override[i];
        if (o.command == "dynamicteaser" || o.command == "textextractor") {
            vespalib::string markupField = o.arguments;
            if (markupField.empty())
                continue;
            // Assume just one argument: source field that must contain markup
            _markupFields.insert(markupField);
        }
    }
    const DocumentType *docType = repo->getDocumentType(docTypeName.getName());
    if (docType != NULL) {
        _fieldCacheRepo.reset(new FieldCacheRepo(getResultConfig(), *docType));
    } else if (getResultConfig().GetNumResultClasses() == 0) {
        LOG(debug, "Create empty field cache repo for document type '%s'", docTypeName.toString().c_str());
        _fieldCacheRepo.reset(new FieldCacheRepo());
    } else {
        throw IllegalArgumentException(make_string("Did not find document type '%s' in current document type repo."
                                                   " Cannot setup field cache repo for the summary setup",
                                                   docTypeName.toString().c_str()));
    }
}

IDocsumStore::UP
SummaryManager::SummarySetup::createDocsumStore(const vespalib::string &resultClassName) {
    return std::make_unique<DocumentStoreAdapter>(*_docStore, *_repo, getResultConfig(), resultClassName,
                                                  _fieldCacheRepo->getFieldCache(resultClassName), _markupFields);
}


ISummaryManager::ISummarySetup::SP
SummaryManager::createSummarySetup(const SummaryConfig & summaryCfg,
                                   const SummarymapConfig & summarymapCfg,
                                   const JuniperrcConfig & juniperCfg,
                                   const DocumentTypeRepo::SP &repo,
                                   const search::IAttributeManager::SP &attributeMgr)
{
    return std::make_shared<SummarySetup>(_baseDir, _docTypeName, summaryCfg, summarymapCfg,
                                          juniperCfg, attributeMgr, _docStore, repo);
}

namespace {

template<typename T>
document::CompressionConfig
deriveCompression(const T & config) {
    document::CompressionConfig compression;
    if (config.type == T::LZ4) {
        compression.type = document::CompressionConfig::LZ4;
    }
    compression.compressionLevel = config.level;
    return compression;
}

DocumentStore::Config
getStoreConfig(const ProtonConfig::Summary::Cache & cache)
{
    return DocumentStore::Config(deriveCompression(cache.compression), cache.maxbytes, cache.initialentries).allowVisitCaching(cache.allowvisitcaching);
}

}

SummaryManager::SummaryManager(vespalib::ThreadExecutor & executor,
                               const ProtonConfig::Summary & summary,
                               const search::GrowStrategy & growStrategy,
                               const vespalib::string &baseDir,
                               const DocTypeName &docTypeName,
                               const TuneFileSummary &tuneFileSummary,
                               const FileHeaderContext &fileHeaderContext,
                               search::transactionlog::SyncProxy &tlSyncer,
                               const search::IBucketizer::SP & bucketizer)
    : _baseDir(baseDir),
      _docTypeName(docTypeName),
      _docStore(),
      _tuneFileSummary(tuneFileSummary),
      _currentSerial(0u)
{
    DocumentStore::Config config(getStoreConfig(summary.cache));
    const ProtonConfig::Summary::Log & log(summary.log);
    const ProtonConfig::Summary::Log::Chunk & chunk(log.chunk);

    WriteableFileChunk::Config fileConfig(deriveCompression(chunk.compression), chunk.maxbytes);
    LogDataStore::Config logConfig(log.maxfilesize, log.maxdiskbloatfactor, log.maxbucketspread,
                                   log.minfilesizefactor, log.numthreads, log.compact2activefile,
                                   deriveCompression(log.compact.compression), fileConfig);
    logConfig.disableCrcOnRead(chunk.skipcrconread);
    _docStore.reset(new LogDocumentStore(executor, baseDir,
                                         LogDocumentStore::Config(config, logConfig),
                                         growStrategy, tuneFileSummary, fileHeaderContext, tlSyncer,
                                         summary.compact2buckets ? bucketizer : search::IBucketizer::SP()));
}

void
SummaryManager::putDocument(uint64_t syncToken, const Document & doc, search::DocumentIdT lid)
{
    _docStore->write(syncToken, doc, lid);
    _currentSerial = syncToken;
}

void
SummaryManager::removeDocument(uint64_t syncToken, search::DocumentIdT lid)
{
    _docStore->remove(syncToken, lid);
    _currentSerial = syncToken;
}

IFlushTarget::List SummaryManager::getFlushTargets()
{
    IFlushTarget::List ret;
    ret.push_back(IFlushTarget::SP(new SummaryFlushTarget(getBackingStore())));
    if (dynamic_cast<LogDocumentStore *>(_docStore.get()) != NULL) {
        ret.push_back(IFlushTarget::SP(new SummaryCompactTarget(getBackingStore())));
    }
    return ret;
}

} // namespace proton
