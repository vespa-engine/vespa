// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_merger.h"
#include "fieldreader.h"
#include "field_length_scanner.h"
#include "fusion_input_index.h"
#include "fusion_output_index.h"
#include "dictionarywordreader.h"
#include "wordnummapper.h"
#include <vespa/fastos/file.h>
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>
#include <vespa/searchlib/common/i_flush_token.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/searchlib/util/postingpriorityqueue.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>

LOG_SETUP(".diskindex.field_merger");

using search::FileKit;
using search::bitcompression::PosOccFieldParams;
using search::bitcompression::PosOccFieldsParams;
using search::common::FileHeaderContext;
using search::index::FieldLengthInfo;
using search::index::PostingListParams;
using search::index::Schema;
using search::index::SchemaUtil;
using search::index::schema::DataType;
using vespalib::IllegalArgumentException;
using vespalib::make_string;

namespace search::diskindex {


namespace {

vespalib::string
createTmpPath(const vespalib::string & base, uint32_t index) {
    vespalib::asciistream os;
    os << base;
    os << "/tmpindex";
    os << index;
    return os.str();
}

}

FieldMerger::FieldMerger(uint32_t id, const FusionOutputIndex& fusion_out_index)
    : _id(id),
      _field_dir(fusion_out_index.get_path() + "/" + SchemaUtil::IndexIterator(fusion_out_index.get_schema(), id).getName()),
      _fusion_out_index(fusion_out_index)
{
}

FieldMerger::~FieldMerger() = default;

void
FieldMerger::make_tmp_dirs()
{
    for (const auto & index : _fusion_out_index.get_old_indexes()) {
        vespalib::mkdir(createTmpPath(_field_dir, index.getIndex()), false);
    }
}

bool
FieldMerger::clean_tmp_dirs()
{
    uint32_t i = 0;
    for (;;) {
        vespalib::string tmpindexpath = createTmpPath(_field_dir, i);
        FastOS_StatInfo statInfo;
        if (!FastOS_File::Stat(tmpindexpath.c_str(), &statInfo)) {
            if (statInfo._error == FastOS_StatInfo::FileNotFound) {
                break;
            }
            LOG(error, "Failed to stat tmpdir %s", tmpindexpath.c_str());
            return false;
        }
        i++;
    }
    while (i > 0) {
        i--;
        vespalib::string tmpindexpath = createTmpPath(_field_dir, i);
        search::DirectoryTraverse dt(tmpindexpath.c_str());
        if (!dt.RemoveTree()) {
            LOG(error, "Failed to clean tmpdir %s", tmpindexpath.c_str());
            return false;
        }
    }
    return true;
}

bool
FieldMerger::open_input_word_readers(std::vector<std::unique_ptr<DictionaryWordReader>> & readers, PostingPriorityQueue<DictionaryWordReader>& heap)
{
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    for (auto & oi : _fusion_out_index.get_old_indexes()) {
        auto reader(std::make_unique<DictionaryWordReader>());
        const vespalib::string &tmpindexpath = createTmpPath(_field_dir, oi.getIndex());
        const vespalib::string &oldindexpath = oi.getPath();
        vespalib::string wordMapName = tmpindexpath + "/old2new.dat";
        vespalib::string fieldDir(oldindexpath + "/" + index.getName());
        vespalib::string dictName(fieldDir + "/dictionary");
        const Schema &oldSchema = oi.getSchema();
        if (!index.hasOldFields(oldSchema)) {
            continue; // drop data
        }
        bool res = reader->open(dictName, wordMapName, _fusion_out_index.get_tune_file_indexing()._read);
        if (!res) {
            LOG(error, "Could not open dictionary %s to generate %s", dictName.c_str(), wordMapName.c_str());
            return false;
        }
        reader->read();
        if (reader->isValid()) {
            readers.push_back(std::move(reader));
            heap.initialAdd(readers.back().get());
        }
    }
    return true;
}

bool
FieldMerger::read_mapping_files(WordNumMappingList& list)
{
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    for (const auto & oi : _fusion_out_index.get_old_indexes()) {
        std::vector<uint32_t> oldIndexes;
        const Schema &oldSchema = oi.getSchema();
        if (!SchemaUtil::getIndexIds(oldSchema, DataType::STRING, oldIndexes)) {
            return false;
        }
        WordNumMapping &wordNumMapping = list[oi.getIndex()];
        if (oldIndexes.empty()) {
            wordNumMapping.noMappingFile();
            continue;
        }
        if (!index.hasOldFields(oldSchema)) {
            continue; // drop data
        }

        // Open word mapping file
        vespalib::string old2newname = createTmpPath(_field_dir, oi.getIndex()) + "/old2new.dat";
        wordNumMapping.readMappingFile(old2newname, _fusion_out_index.get_tune_file_indexing()._read);
    }

    return true;
}

bool
FieldMerger::renumber_word_ids(WordNumMappingList& list, uint64_t& numWordIds, const IFlushToken& flush_token)
{
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    vespalib::string indexName = index.getName();
    LOG(debug, "Renumber word IDs for field %s", indexName.c_str());

    std::vector<std::unique_ptr<DictionaryWordReader>> readers;
    PostingPriorityQueue<DictionaryWordReader> heap;
    WordAggregator out;

    if (!open_input_word_readers(readers, heap)) {
        return false;
    }
    heap.merge(out, 4, flush_token);
    if (flush_token.stop_requested()) {
        return false;
    }
    assert(heap.empty());
    numWordIds = out.getWordNum();

    // Close files
    for (auto &i : readers) {
        i->close();
    }

    // Now read mapping files back into an array
    // XXX: avoid this, and instead make the array here
    if (!read_mapping_files(list)) {
        return false;
    }
    LOG(debug, "Finished renumbering words IDs for field %s", indexName.c_str());

    return true;
}

std::shared_ptr<FieldLengthScanner>
FieldMerger::allocate_field_length_scanner()
{
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    if (index.use_interleaved_features()) {
        PosOccFieldsParams fieldsParams;
        fieldsParams.setSchemaParams(index.getSchema(), index.getIndex());
        assert(fieldsParams.getNumFields() > 0);
        const PosOccFieldParams &fieldParams = fieldsParams.getFieldParams()[0];
        if (fieldParams._hasElements) {
            for (const auto &old_index : _fusion_out_index.get_old_indexes()) {
                const Schema &old_schema = old_index.getSchema();
                if (index.hasOldFields(old_schema) &&
                    !index.has_matching_use_interleaved_features(old_schema)) {
                    return std::make_shared<FieldLengthScanner>(_fusion_out_index.get_doc_id_limit());
                }
            }
        }
    }
    return std::shared_ptr<FieldLengthScanner>();
}

bool
FieldMerger::open_input_field_readers(const WordNumMappingList& list, std::vector<std::unique_ptr<FieldReader>>& readers)
{
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    auto field_length_scanner = allocate_field_length_scanner();
    vespalib::string indexName = index.getName();
    for (const auto &oi : _fusion_out_index.get_old_indexes()) {
        const Schema &oldSchema = oi.getSchema();
        if (!index.hasOldFields(oldSchema)) {
            continue; // drop data
        }
        auto reader = FieldReader::allocFieldReader(index, oldSchema, field_length_scanner);
        reader->setup(list[oi.getIndex()], oi.getDocIdMapping());
        if (!reader->open(oi.getPath() + "/" + indexName + "/", _fusion_out_index.get_tune_file_indexing()._read)) {
            return false;
        }
        readers.push_back(std::move(reader));
    }
    return true;
}

bool
FieldMerger::open_field_writer(FieldWriter& writer, const FieldLengthInfo& field_length_info)
{
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    if (!writer.open(_field_dir + "/", 64, 262144, _fusion_out_index.get_dynamic_k_pos_index_format(),
                     index.use_interleaved_features(), index.getSchema(),
                     index.getIndex(),
                     field_length_info,
                     _fusion_out_index.get_tune_file_indexing()._write, _fusion_out_index.get_file_header_context())) {
        throw IllegalArgumentException(make_string("Could not open output posocc + dictionary in %s", _field_dir.c_str()));
    }
    return true;
}

bool
FieldMerger::select_cooked_or_raw_features(FieldReader& reader, FieldWriter& writer)
{
    bool rawFormatOK = true;
    bool cookedFormatOK = true;
    PostingListParams featureParams;
    PostingListParams outFeatureParams;
    vespalib::string cookedFormat;
    vespalib::string rawFormat;

    if (!reader.isValid()) {
        return true;
    }
    {
        writer.getFeatureParams(featureParams);
        cookedFormat = featureParams.getStr("cookedEncoding");
        rawFormat = featureParams.getStr("encoding");
        if (rawFormat == "") {
            rawFormatOK = false;    // Typically uncompressed file
        }
        outFeatureParams = featureParams;
    }
    {
        reader.getFeatureParams(featureParams);
        if (cookedFormat != featureParams.getStr("cookedEncoding")) {
            cookedFormatOK = false;
        }
        if (rawFormat != featureParams.getStr("encoding")) {
            rawFormatOK = false;
        }
        if (featureParams != outFeatureParams) {
            rawFormatOK = false;
        }
        if (!reader.allowRawFeatures()) {
            rawFormatOK = false;    // Reader transforms data
        }
    }
    if (!cookedFormatOK) {
        LOG(error, "Cannot perform fusion, cooked feature formats don't match");
        return false;
    }
    if (rawFormatOK) {
        featureParams.clear();
        featureParams.set("cooked", false);
        reader.setFeatureParams(featureParams);
        reader.getFeatureParams(featureParams);
        if (featureParams.isSet("cookedEncoding") ||
            rawFormat != featureParams.getStr("encoding")) {
            rawFormatOK = false;
        }
        if (!rawFormatOK) {
            LOG(error, "Cannot perform fusion, raw format setting failed");
            return false;
        }
        LOG(debug, "Using raw feature format for fusion of posting files");
    }
    return true;
}

bool
FieldMerger::setup_merge_heap(const std::vector<std::unique_ptr<FieldReader>>& readers, FieldWriter& writer, PostingPriorityQueue<FieldReader>& heap)
{
    for (auto &reader : readers) {
        if (!select_cooked_or_raw_features(*reader, writer)) {
            return false;
        }
        if (reader->isValid()) {
            reader->read();
        }
        if (reader->isValid()) {
            heap.initialAdd(reader.get());
        }
    }
    return true;
}

bool
FieldMerger::merge_postings(const WordNumMappingList& list, uint64_t numWordIds, const IFlushToken& flush_token)
{
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    std::vector<std::unique_ptr<FieldReader>> readers;
    PostingPriorityQueue<FieldReader> heap;
    /* OUTPUT */
    FieldWriter fieldWriter(_fusion_out_index.get_doc_id_limit(), numWordIds);
    vespalib::string indexName = index.getName();

    if (!open_input_field_readers(list, readers)) {
        return false;
    }
    FieldLengthInfo field_length_info;
    if (!readers.empty()) {
        field_length_info = readers.back()->get_field_length_info();
    }
    if (!open_field_writer(fieldWriter, field_length_info)) {
        return false;
    }
    if (!setup_merge_heap(readers, fieldWriter, heap)) {
        return false;
    }

    heap.merge(fieldWriter, 4, flush_token);
    if (flush_token.stop_requested()) {
        return false;
    }
    assert(heap.empty());

    for (auto &reader : readers) {
        if (!reader->close()) {
            return false;
        }
    }
    if (!fieldWriter.close()) {
        throw IllegalArgumentException(make_string("Could not close output posocc + dictionary in %s", _field_dir.c_str()));
    }
    return true;
}

bool
FieldMerger::merge_field(std::shared_ptr<IFlushToken> flush_token)
{
    const Schema &schema = _fusion_out_index.get_schema();
    SchemaUtil::IndexIterator index(schema, _id);
    const vespalib::string &indexName = index.getName();
    SchemaUtil::IndexSettings settings = index.getIndexSettings();
    if (settings.hasError()) {
        return false;
    }

    if (FileKit::hasStamp(_field_dir + "/.mergeocc_done")) {
        return true;
    }
    vespalib::mkdir(_field_dir, false);

    LOG(debug, "merge_field for field %s dir %s", indexName.c_str(), _field_dir.c_str());

    make_tmp_dirs();

    WordNumMappingList list(_fusion_out_index.get_old_indexes().size());
    uint64_t numWordIds(0);
    if (!renumber_word_ids(list, numWordIds, *flush_token)) {
        if (flush_token->stop_requested()) {
            return false;
        }
        LOG(error, "Could not renumber field word ids for field %s dir %s", indexName.c_str(), _field_dir.c_str());
        return false;
    }

    // Tokamak
    bool res = merge_postings(list, numWordIds, *flush_token);
    if (!res) {
        if (flush_token->stop_requested()) {
            return false;
        }
        throw IllegalArgumentException(make_string("Could not merge field postings for field %s dir %s",
                                                   indexName.c_str(), _field_dir.c_str()));
    }
    if (!FileKit::createStamp(_field_dir +  "/.mergeocc_done")) {
        return false;
    }
    vespalib::File::sync(_field_dir);

    if (!clean_tmp_dirs()) {
        return false;
    }

    LOG(debug, "Finished merge_field for field %s dir %s", indexName.c_str(), _field_dir.c_str());

    return true;
}

}
