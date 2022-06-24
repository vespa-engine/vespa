// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fusion.h"
#include "fusion_input_index.h"
#include "field_merger.h"
#include "field_mergers_state.h"
#include <vespa/fastos/file.h>
#include <vespa/searchlib/common/documentsummary.h>
#include <vespa/searchlib/common/i_flush_token.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/error.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/document/util/queue.h>
#include <filesystem>
#include <system_error>

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
    assert(sources.size() <= 255); // due to source selector data type
    std::vector<FusionInputIndex> indexes;
    indexes.reserve(sources.size());
    uint32_t i = 0;
    for (const auto & source : sources) {
        indexes.emplace_back(source, i++, selector);
    }
    return indexes;
}

uint32_t calc_trimmed_doc_id_limit(const SelectorArray& selector, const std::vector<vespalib::string>& sources)
{
    uint32_t docIdLimit = selector.size();
    uint32_t trimmed_doc_id_limit = docIdLimit;

    // Limit docIdLimit in output based on selections that cannot be satisfied
    uint32_t sources_size = sources.size();
    while (trimmed_doc_id_limit > 0 && selector[trimmed_doc_id_limit - 1] >= sources_size) {
        --trimmed_doc_id_limit;
    }
    return trimmed_doc_id_limit;
}

}

Fusion::Fusion(const Schema& schema, const vespalib::string& dir,
               const std::vector<vespalib::string>& sources, const SelectorArray& selector,
               const TuneFileIndexing& tuneFileIndexing,
               const FileHeaderContext& fileHeaderContext)
    : _old_indexes(createInputIndexes(sources, selector)),
      _fusion_out_index(schema, dir, _old_indexes, calc_trimmed_doc_id_limit(selector, sources), tuneFileIndexing, fileHeaderContext)
{
}

Fusion::~Fusion() = default;

bool
Fusion::mergeFields(vespalib::Executor& shared_executor, std::shared_ptr<IFlushToken> flush_token)
{
    FieldMergersState field_mergers_state(_fusion_out_index, shared_executor, flush_token);
    const Schema &schema = getSchema();
    for (SchemaUtil::IndexIterator iter(schema); iter.isValid(); ++iter) {
        auto& field_merger = field_mergers_state.alloc_field_merger(iter.getIndex());
        field_mergers_state.schedule_task(field_merger);
    }
    LOG(debug, "Waiting for %u fields", schema.getNumIndexFields());
    field_mergers_state.wait_field_mergers_done();
    LOG(debug, "Done waiting for %u fields", schema.getNumIndexFields());
    return (field_mergers_state.get_failed() == 0u);
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
Fusion::merge(vespalib::Executor& shared_executor, std::shared_ptr<IFlushToken> flush_token)
{
    FastOS_StatInfo statInfo;
    if (!FastOS_File::Stat(_fusion_out_index.get_path().c_str(), &statInfo)) {
        if (statInfo._error != FastOS_StatInfo::FileNotFound) {
            LOG(error, "Could not stat \"%s\"", _fusion_out_index.get_path().c_str());
            return false;
        }
    } else {
        if (!statInfo._isDirectory) {
            LOG(error, "\"%s\" is not a directory", _fusion_out_index.get_path().c_str());
            return false;
        }
        std::error_code ec;
        std::filesystem::remove_all(std::filesystem::path(_fusion_out_index.get_path()), ec);
        if (ec) {
            LOG(error, "Failed to clean directory \"%s\"", _fusion_out_index.get_path().c_str());
            return false;
        }
    }

    std::filesystem::create_directory(std::filesystem::path(_fusion_out_index.get_path()));
    _fusion_out_index.get_schema().saveToFile(_fusion_out_index.get_path() + "/schema.txt");
    if (!DocumentSummary::writeDocIdLimit(_fusion_out_index.get_path(), _fusion_out_index.get_doc_id_limit())) {
        LOG(error, "Could not write docsum count in dir %s: %s", _fusion_out_index.get_path().c_str(), getLastErrorString().c_str());
        return false;
    }

    try {
        for (auto& old_index : _old_indexes) {
            old_index.setup();
        }
        if (!readSchemaFiles()) {
            throw IllegalArgumentException("Cannot read schema files for source indexes");
        }
        return mergeFields(shared_executor, flush_token);
    } catch (const std::exception & e) {
        LOG(error, "%s", e.what());
        return false;
    }
}

}
