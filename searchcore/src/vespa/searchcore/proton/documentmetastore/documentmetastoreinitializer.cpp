// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentmetastoreinitializer.h"

#include "documentmetastore.h"

#include <vespa/check_require.h>
#include <vespa/opentelemetry/span_attribute_names.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcore/proton/common/memory_usage_logger.h>
#include <vespa/searchcore/proton/common/tracing.h>
#include <vespa/searchlib/common/indexmetainfo.h>
#include <vespa/vespalib/util/exceptions.h>

#include <filesystem>

using proton::initializer::InitializerTask;
using search::GrowStrategy;
using search::IndexMetaInfo;
using vespalib::IllegalStateException;
using vespalib::make_string;

namespace proton::documentmetastore {

DocumentMetaStoreInitializer::DocumentMetaStoreInitializer(const std::string                  baseDir,
                                                           const DocTypeName&                 doc_type_name_in,
                                                           const std::string&                 subdb_name_in,
                                                           std::shared_ptr<DocumentMetaStore> dms)
    : _baseDir(baseDir), _doc_type_name(doc_type_name_in), _subdb_name(subdb_name_in), _dms(std::move(dms)) {
}

namespace {
std::string failedMsg(const char* msg) {
    return make_string("Failed to load document meta store for document type '%s' from disk", msg);
}
} // namespace

void DocumentMetaStoreInitializer::run() {
    auto tracer = get_tracer();
    auto span =
        tracer->StartSpan("Load documentmetastore",
                          {{vespa_opentelemetry::span_attribute_names::document_type, _doc_type_name.getName()},
                           {vespa_opentelemetry::span_attribute_names::subdb, _subdb_name}});
    auto          scope = tracer->WithActiveSpan(span);
    std::string   name = DocumentMetaStore::getFixedName();
    IndexMetaInfo info(_baseDir);
    if (info.load()) {
        IndexMetaInfo::Snapshot snap = info.getBestSnapshot();
        if (snap.valid) {
            std::string attrFileName = _baseDir + "/" + snap.dirName + "/" + name;
            _dms->setBaseFileName(attrFileName);
            CHECK(_dms->hasLoadData());
            vespalib::Timer stopWatch;
            std::string document_type_and_subdb_name = std::format("{}.{}", _doc_type_name.getName(), _subdb_name);

            EventLogger::loadDocumentMetaStoreStart(document_type_and_subdb_name);
            MemoryUsageLogger::log("start load documentmetastore", document_type_and_subdb_name);
            if (!_dms->load()) {
                throw IllegalStateException(failedMsg(_doc_type_name.getName().c_str()));
            } else {
                _dms->commit(search::CommitParam(snap.syncToken, search::CommitParam::UpdateStats::SKIP));
            }
            EventLogger::loadDocumentMetaStoreComplete(document_type_and_subdb_name, stopWatch.elapsed());
            MemoryUsageLogger::log("finished load documentmetastore", document_type_and_subdb_name);
        }
    } else {
        std::filesystem::create_directory(std::filesystem::path(_baseDir));
        info.save();
    }
    span->End();
}

} // namespace proton::documentmetastore
