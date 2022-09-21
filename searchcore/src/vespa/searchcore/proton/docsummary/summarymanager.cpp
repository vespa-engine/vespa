// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summarymanager.h"
#include "documentstoreadapter.h"
#include "summarycompacttarget.h"
#include "summaryflushtarget.h"
#include <vespa/config/print/ostreamconfigwriter.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/juniper/rpinterface.h>
#include <vespa/searchcore/proton/flushengine/shrink_lid_space_flush_target.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/searchsummary/docsummary/docsum_field_writer_factory.h>
#include <vespa/searchsummary/docsummary/keywordextractor.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/fastlib/text/normwordfolder.h>
#include <vespa/config-summary.h>

#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.summarymanager");

using namespace config;
using namespace document;
using namespace search::docsummary;
using vespalib::make_string;
using vespalib::IllegalArgumentException;
using vespalib::compression::CompressionConfig;

using search::DocumentStore;
using search::IDocumentStore;
using search::LogDocumentStore;
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
    vespalib::Executor & _summaryService;

public:
    ShrinkSummaryLidSpaceFlushTarget(const vespalib::string &name, Type type, Component component,
                                     SerialNum flushedSerialNum, vespalib::system_time lastFlushTime,
                                     vespalib::Executor & summaryService,
                                     std::shared_ptr<ICompactableLidSpace> target);
    ~ShrinkSummaryLidSpaceFlushTarget() override;
    Task::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token) override;
};

ShrinkSummaryLidSpaceFlushTarget::
ShrinkSummaryLidSpaceFlushTarget(const vespalib::string &name, Type type, Component component,
                                 SerialNum flushedSerialNum, vespalib::system_time lastFlushTime,
                                 vespalib::Executor & summaryService,
                                 std::shared_ptr<ICompactableLidSpace> target)
    : ShrinkLidSpaceFlushTarget(name, type, component, flushedSerialNum, lastFlushTime, std::move(target)),
      _summaryService(summaryService)
{
}

ShrinkSummaryLidSpaceFlushTarget::~ShrinkSummaryLidSpaceFlushTarget() = default;

IFlushTarget::Task::UP
ShrinkSummaryLidSpaceFlushTarget::initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken> flush_token)
{
    std::promise<Task::UP> promise;
    std::future<Task::UP> future = promise.get_future();
    _summaryService.execute(makeLambdaTask([&]() { promise.set_value(ShrinkLidSpaceFlushTarget::initFlush(currentSerial, flush_token)); }));
    return future.get();
}

}

SummaryManager::SummarySetup::
SummarySetup(const vespalib::string & baseDir, const SummaryConfig & summaryCfg,
             const JuniperrcConfig & juniperCfg,
             search::IAttributeManager::SP attributeMgr, search::IDocumentStore::SP docStore,
             std::shared_ptr<const DocumentTypeRepo> repo)
    : _docsumWriter(),
      _wordFolder(std::make_unique<Fast_NormalizeWordFolder>()),
      _juniperProps(juniperCfg),
      _juniperConfig(),
      _attributeMgr(std::move(attributeMgr)),
      _docStore(std::move(docStore)),
      _repo(std::move(repo))
{
    _juniperConfig = std::make_unique<juniper::Juniper>(&_juniperProps, _wordFolder.get());
    auto resultConfig = std::make_unique<ResultConfig>();
    auto docsum_field_writer_factory = std::make_unique<DocsumFieldWriterFactory>(summaryCfg.usev8geopositions, *this);
    if (!resultConfig->readConfig(summaryCfg, make_string("SummaryManager(%s)", baseDir.c_str()).c_str(),
                                  *docsum_field_writer_factory)) {
        std::ostringstream oss;
        ::config::OstreamConfigWriter writer(oss);
        writer.write(summaryCfg);
        throw IllegalArgumentException
            (make_string("Could not initialize summary result config for directory '%s' based on summary config '%s'",
                         baseDir.c_str(), oss.str().c_str()));
    }
    docsum_field_writer_factory.reset();

    _docsumWriter = std::make_unique<DynamicDocsumWriter>(std::move(resultConfig), std::unique_ptr<KeywordExtractor>());
}

IDocsumStore::UP
SummaryManager::SummarySetup::createDocsumStore()
{
    return std::make_unique<DocumentStoreAdapter>(*_docStore, *_repo);
}


ISummaryManager::ISummarySetup::SP
SummaryManager::createSummarySetup(const SummaryConfig & summaryCfg,
                                   const JuniperrcConfig & juniperCfg, const std::shared_ptr<const DocumentTypeRepo> &repo,
                                   const search::IAttributeManager::SP &attributeMgr)
{
    return std::make_shared<SummarySetup>(_baseDir, summaryCfg,
                                          juniperCfg, attributeMgr, _docStore, repo);
}

SummaryManager::SummaryManager(vespalib::Executor &shared_executor, const LogDocumentStore::Config & storeConfig,
                               const search::GrowStrategy & growStrategy, const vespalib::string &baseDir,
                               const TuneFileSummary &tuneFileSummary,
                               const FileHeaderContext &fileHeaderContext, search::transactionlog::SyncProxy &tlSyncer,
                               search::IBucketizer::SP bucketizer)
    : _baseDir(baseDir),
      _docStore()
{
    _docStore = std::make_shared<LogDocumentStore>(shared_executor, baseDir, storeConfig, growStrategy, tuneFileSummary,
                                                   fileHeaderContext, tlSyncer, std::move(bucketizer));
}

SummaryManager::~SummaryManager() = default;

void
SummaryManager::putDocument(uint64_t syncToken, search::DocumentIdT lid, const Document & doc)
{
    _docStore->write(syncToken, lid, doc);
}

void
SummaryManager::putDocument(uint64_t syncToken, search::DocumentIdT lid, const vespalib::nbostream & doc)
{
    _docStore->write(syncToken, lid, doc);
}

void
SummaryManager::removeDocument(uint64_t syncToken, search::DocumentIdT lid)
{
    _docStore->remove(syncToken, lid);
}

namespace {

IFlushTarget::SP
createShrinkLidSpaceFlushTarget(vespalib::Executor & summaryService, IDocumentStore::SP docStore)
{
    return std::make_shared<ShrinkSummaryLidSpaceFlushTarget>("summary.shrink",
                                                       IFlushTarget::Type::GC,
                                                       IFlushTarget::Component::DOCUMENT_STORE,
                                                       docStore->lastSyncToken(),
                                                       docStore->getLastFlushTime(),
                                                       summaryService,
                                                       std::move(docStore));
}

}

IFlushTarget::List
SummaryManager::getFlushTargets(vespalib::Executor & summaryService)
{
    IFlushTarget::List ret;
    ret.push_back(std::make_shared<SummaryFlushTarget>(getBackingStore(), summaryService));
    if (dynamic_cast<LogDocumentStore *>(_docStore.get()) != nullptr) {
        ret.push_back(std::make_shared<SummaryCompactBloatTarget>(summaryService, getBackingStore()));
        ret.push_back(std::make_shared<SummaryCompactSpreadTarget>(summaryService, getBackingStore()));
    }
    ret.push_back(createShrinkLidSpaceFlushTarget(summaryService, _docStore));
    return ret;
}

void
SummaryManager::reconfigure(const LogDocumentStore::Config & config) {
    auto & docStore = dynamic_cast<LogDocumentStore &> (*_docStore);
    docStore.reconfigure(config);
}

} // namespace proton
