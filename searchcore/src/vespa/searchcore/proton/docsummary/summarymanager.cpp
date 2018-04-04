// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentstoreadapter.h"
#include "summarycompacttarget.h"
#include "summaryflushtarget.h"
#include "summarymanager.h"
#include <vespa/config/print/ostreamconfigwriter.h>
#include <vespa/juniper/rpinterface.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/searchsummary/docsummary/docsumconfig.h>
#include <vespa/vespalib/util/exceptions.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.summarymanager");

using namespace config;
using namespace document;
using namespace search::docsummary;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using vespalib::make_string;
using vespalib::IllegalArgumentException;
using vespalib::compression::CompressionConfig;

using search::DocumentStore;
using search::IDocumentStore;
using search::LogDocumentStore;
using search::LogDataStore;
using search::WriteableFileChunk;
using vespalib::makeLambdaTask;

using search::TuneFileSummary;
using search::common::FileHeaderContext;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {

class ShrinkSummaryLidSpaceFlushTarget : public  ShrinkLidSpaceFlushTarget
{
    using ICompactableLidSpace = search::common::ICompactableLidSpace;
    searchcorespi::index::IThreadService & _summaryService;

public:
    ShrinkSummaryLidSpaceFlushTarget(const vespalib::string &name, Type type, Component component,
                                     SerialNum flushedSerialNum, Time lastFlushTime,
                                     searchcorespi::index::IThreadService & summaryService,
                                     std::shared_ptr<ICompactableLidSpace> target);
    ~ShrinkSummaryLidSpaceFlushTarget();
    Task::UP initFlush(SerialNum currentSerial) override;
};

ShrinkSummaryLidSpaceFlushTarget::
ShrinkSummaryLidSpaceFlushTarget(const vespalib::string &name, Type type, Component component,
                                 SerialNum flushedSerialNum, Time lastFlushTime,
                                 searchcorespi::index::IThreadService & summaryService,
                                 std::shared_ptr<ICompactableLidSpace> target)
    : ShrinkLidSpaceFlushTarget(name, type, component, flushedSerialNum, lastFlushTime, std::move(target)),
      _summaryService(summaryService)
{
}

ShrinkSummaryLidSpaceFlushTarget::~ShrinkSummaryLidSpaceFlushTarget() {}

IFlushTarget::Task::UP
ShrinkSummaryLidSpaceFlushTarget::initFlush(SerialNum currentSerial)
{
    std::promise<Task::UP> promise;
    std::future<Task::UP> future = promise.get_future();
    _summaryService.execute(makeLambdaTask([&]() { promise.set_value(ShrinkLidSpaceFlushTarget::initFlush(currentSerial)); }));
    return future.get();
}

}

SummaryManager::SummarySetup::
SummarySetup(const vespalib::string & baseDir, const DocTypeName & docTypeName, const SummaryConfig & summaryCfg,
             const SummarymapConfig & summarymapCfg, const JuniperrcConfig & juniperCfg,
             const search::IAttributeManager::SP &attributeMgr, const search::IDocumentStore::SP & docStore,
             const std::shared_ptr<const DocumentTypeRepo> &repo)
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
SummaryManager::createSummarySetup(const SummaryConfig & summaryCfg, const SummarymapConfig & summarymapCfg,
                                   const JuniperrcConfig & juniperCfg, const std::shared_ptr<const DocumentTypeRepo> &repo,
                                   const search::IAttributeManager::SP &attributeMgr)
{
    return std::make_shared<SummarySetup>(_baseDir, _docTypeName, summaryCfg, summarymapCfg,
                                          juniperCfg, attributeMgr, _docStore, repo);
}

SummaryManager::SummaryManager(vespalib::ThreadExecutor & executor, const LogDocumentStore::Config & storeConfig,
                               const search::GrowStrategy & growStrategy, const vespalib::string &baseDir,
                               const DocTypeName &docTypeName, const TuneFileSummary &tuneFileSummary,
                               const FileHeaderContext &fileHeaderContext, search::transactionlog::SyncProxy &tlSyncer,
                               const search::IBucketizer::SP & bucketizer)
    : _baseDir(baseDir),
      _docTypeName(docTypeName),
      _docStore(),
      _tuneFileSummary(tuneFileSummary),
      _currentSerial(0u)
{
    _docStore = std::make_shared<LogDocumentStore>(executor, baseDir, storeConfig, growStrategy, tuneFileSummary,
                                                   fileHeaderContext, tlSyncer, bucketizer);
}

SummaryManager::~SummaryManager() {}

void
SummaryManager::putDocument(uint64_t syncToken, search::DocumentIdT lid, const Document & doc)
{
    _docStore->write(syncToken, lid, doc);
    _currentSerial = syncToken;
}

void
SummaryManager::putDocument(uint64_t syncToken, search::DocumentIdT lid, const vespalib::nbostream & doc)
{
    _docStore->write(syncToken, lid, doc);
    _currentSerial = syncToken;
}

void
SummaryManager::removeDocument(uint64_t syncToken, search::DocumentIdT lid)
{
    _docStore->remove(syncToken, lid);
    _currentSerial = syncToken;
}

namespace {

IFlushTarget::SP
createShrinkLidSpaceFlushTarget(searchcorespi::index::IThreadService & summaryService, IDocumentStore::SP docStore)
{
    return std::make_shared<ShrinkSummaryLidSpaceFlushTarget>("summary.shrink",
                                                       IFlushTarget::Type::GC,
                                                       IFlushTarget::Component::DOCUMENT_STORE,
                                                       docStore->lastSyncToken(),
                                                       docStore->getLastFlushTime(),
                                                       summaryService,
                                                       docStore);
}

}

IFlushTarget::List SummaryManager::getFlushTargets(searchcorespi::index::IThreadService & summaryService)
{
    IFlushTarget::List ret;
    ret.push_back(std::make_shared<SummaryFlushTarget>(getBackingStore(), summaryService));
    if (dynamic_cast<LogDocumentStore *>(_docStore.get()) != NULL) {
        ret.push_back(std::make_shared<SummaryCompactTarget>(summaryService, getBackingStore()));
    }
    ret.push_back(createShrinkLidSpaceFlushTarget(summaryService, _docStore));
    return ret;
}

void SummaryManager::reconfigure(const LogDocumentStore::Config & config) {
    LogDocumentStore & docStore = dynamic_cast<LogDocumentStore &> (*_docStore);
    docStore.reconfigure(config);
}

} // namespace proton
