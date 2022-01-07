// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fusion.h"
#include "fusion_input_index.h"
#include "field_merger.h"
#include <vespa/fastos/file.h>
#include <vespa/searchlib/common/documentsummary.h>
#include <vespa/searchlib/common/i_flush_token.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/vespalib/util/error.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/document/util/queue.h>

#include <vespa/log/log.h>

LOG_SETUP(".diskindex.fusion");

using search::common::FileHeaderContext;
using search::docsummary::DocumentSummary;
using search::index::Schema;
using search::index::SchemaUtil;
using search::index::schema::DataType;
using vespalib::getLastErrorString;
using vespalib::IllegalArgumentException;

namespace search::diskindex {

namespace {

std::vector<FusionInputIndex>
createInputIndexes(const std::vector<vespalib::string> & sources, const SelectorArray &selector)
{
    std::vector<FusionInputIndex> indexes;
    indexes.reserve(sources.size());
    uint32_t i = 0;
    for (const auto & source : sources) {
        indexes.emplace_back(source, i++, selector);
    }
    return indexes;
}

}

Fusion::Fusion(uint32_t docIdLimit, const Schema & schema, const vespalib::string & dir,
               const std::vector<vespalib::string> & sources, const SelectorArray &selector,
               bool dynamicKPosIndexFormat, const TuneFileIndexing &tuneFileIndexing,
               const FileHeaderContext &fileHeaderContext)
    : _fusion_out_index(schema, dir, createInputIndexes(sources, selector), docIdLimit, dynamicKPosIndexFormat, tuneFileIndexing, fileHeaderContext)
{
    if (!readSchemaFiles()) {
        throw IllegalArgumentException("Cannot read schema files for source indexes");
    }
}

Fusion::~Fusion() = default;

bool
Fusion::mergeFields(vespalib::ThreadExecutor & executor, std::shared_ptr<IFlushToken> flush_token)
{
    const Schema &schema = getSchema();
    std::atomic<uint32_t> failed(0);
    uint32_t maxConcurrentThreads = std::max(1ul, executor.getNumThreads()/2);
    document::Semaphore concurrent(maxConcurrentThreads);
    vespalib::CountDownLatch  done(schema.getNumIndexFields());
    for (SchemaUtil::IndexIterator iter(schema); iter.isValid(); ++iter) {
        concurrent.wait();
        executor.execute(vespalib::makeLambdaTask([this, index=iter.getIndex(), &failed, &done, &concurrent, flush_token]() {
            FieldMerger merger(index, _fusion_out_index, flush_token);
            if (!merger.merge_field()) {
                failed++;
            }
            concurrent.post();
            done.countDown();
        }));
    }
    LOG(debug, "Waiting for %u fields", schema.getNumIndexFields());
    done.await();
    LOG(debug, "Done waiting for %u fields", schema.getNumIndexFields());
    return (failed == 0u);
}


bool
Fusion::checkSchemaCompat()
{
    /* TODO: Check compatibility */
    return true;
}

bool
Fusion::readSchemaFiles()
{
    bool res = checkSchemaCompat();
    if (!res) {
        LOG(error, "Index fusion cannot continue due to incompatible indexes");
    }
    return res;
}

bool
Fusion::merge(const Schema &schema, const vespalib::string &dir, const std::vector<vespalib::string> &sources,
              const SelectorArray &selector, bool dynamicKPosOccFormat,
              const TuneFileIndexing &tuneFileIndexing, const FileHeaderContext &fileHeaderContext,
              vespalib::ThreadExecutor & executor,
              std::shared_ptr<IFlushToken> flush_token)
{
    assert(sources.size() <= 255);
    uint32_t docIdLimit = selector.size();
    uint32_t trimmedDocIdLimit = docIdLimit;

    // Limit docIdLimit in output based on selections that cannot be satisfied
    uint32_t sourcesSize = sources.size();
    while (trimmedDocIdLimit > 0 && selector[trimmedDocIdLimit - 1] >= sourcesSize) {
        --trimmedDocIdLimit;
    }

    FastOS_StatInfo statInfo;
    if (!FastOS_File::Stat(dir.c_str(), &statInfo)) {
        if (statInfo._error != FastOS_StatInfo::FileNotFound) {
            LOG(error, "Could not stat \"%s\"", dir.c_str());
            return false;
        }
    } else {
        if (!statInfo._isDirectory) {
            LOG(error, "\"%s\" is not a directory", dir.c_str());
            return false;
        }
        search::DirectoryTraverse dt(dir.c_str());
        if (!dt.RemoveTree()) {
            LOG(error, "Failed to clean directory \"%s\"", dir.c_str());
            return false;
        }
    }

    vespalib::mkdir(dir, false);
    schema.saveToFile(dir + "/schema.txt");
    if (!DocumentSummary::writeDocIdLimit(dir, trimmedDocIdLimit)) {
        LOG(error, "Could not write docsum count in dir %s: %s", dir.c_str(), getLastErrorString().c_str());
        return false;
    }

    try {
        auto fusion = std::make_unique<Fusion>(trimmedDocIdLimit, schema, dir, sources, selector,
                                               dynamicKPosOccFormat, tuneFileIndexing, fileHeaderContext);
        return fusion->mergeFields(executor, flush_token);
    } catch (const std::exception & e) {
        LOG(error, "%s", e.what());
        return false;
    }
}

}
