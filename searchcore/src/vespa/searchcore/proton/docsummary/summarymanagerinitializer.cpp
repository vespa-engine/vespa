// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summarymanagerinitializer.h"

#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcore/proton/common/memory_usage_logger.h>
#include <vespa/searchcore/proton/common/tracing.h>

#include <filesystem>

namespace proton {

SummaryManagerInitializer::SummaryManagerInitializer(
    const search::GrowStrategy& grow, const std::string& baseDir,
    std::shared_ptr<const DocTypeAndSubDbName> doc_type_and_subdb_name_in, vespalib::Executor& shared_executor,
    const search::LogDocumentStore::Config& storeCfg, const search::TuneFileSummary& tuneFile,
    const search::common::FileHeaderContext& fileHeaderContext, search::transactionlog::SyncProxy& tlSyncer,
    IBucketizerSP bucketizer, std::shared_ptr<const search::IDocumentIdProvider> document_id_provider,
    std::shared_ptr<SummaryManager::SP> result)
    : proton::initializer::InitializerTask(),
      _grow(grow),
      _baseDir(baseDir),
      _doc_type_and_subdb_name(std::move(doc_type_and_subdb_name_in)),
      _shared_executor(shared_executor),
      _storeCfg(storeCfg),
      _tuneFile(tuneFile),
      _fileHeaderContext(fileHeaderContext),
      _tlSyncer(tlSyncer),
      _bucketizer(std::move(bucketizer)),
      _document_id_provider(std::move(document_id_provider)),
      _result(std::move(result)) {
}

SummaryManagerInitializer::~SummaryManagerInitializer() = default;

void SummaryManagerInitializer::run() {
    auto tracer = get_tracer();
    auto span = tracer->StartSpan("Load document store", {{vespa_opentelemetry::span_attribute_names::document_type,
                                                           _doc_type_and_subdb_name->doc_type_name().getName()},
                                                          {vespa_opentelemetry::span_attribute_names::subdb,
                                                           _doc_type_and_subdb_name->subdb_name()}});
    auto scope = tracer->WithActiveSpan(span);
    std::filesystem::create_directory(std::filesystem::path(_baseDir));
    std::string     document_type_and_subdb_name = _doc_type_and_subdb_name->combined_name();
    vespalib::Timer timer;
    EventLogger::loadDocumentStoreStart(document_type_and_subdb_name);
    MemoryUsageLogger::log("start load document store", document_type_and_subdb_name);
    *_result = std::make_shared<SummaryManager>(_shared_executor, _storeCfg, _grow, _baseDir, _tuneFile,
                                                _fileHeaderContext, _tlSyncer, _bucketizer, _document_id_provider);
    EventLogger::loadDocumentStoreComplete(document_type_and_subdb_name, timer.elapsed());
    MemoryUsageLogger::log("finish load document store", document_type_and_subdb_name);
    span->End();
}

} // namespace proton
