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
#include <vespa/searchlib/util/posting_priority_queue_merger.hpp>
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

FieldMerger::FieldMerger(uint32_t id, const FusionOutputIndex& fusion_out_index, std::shared_ptr<IFlushToken> flush_token)
    : _id(id),
      _field_name(SchemaUtil::IndexIterator(fusion_out_index.get_schema(), id).getName()),
      _field_dir(fusion_out_index.get_path() + "/" + _field_name),
      _fusion_out_index(fusion_out_index),
      _flush_token(std::move(flush_token)),
      _word_readers(),
      _word_heap(),
      _word_aggregator(),
      _word_num_mappings(),
      _num_word_ids(0),
      _readers(),
      _heap(),
      _writer(),
      _state(State::MERGE_START),
      _failed(false)
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
FieldMerger::open_input_word_readers()
{
    _word_readers.reserve(_fusion_out_index.get_old_indexes().size());
    _word_heap = std::make_unique<PostingPriorityQueueMerger<DictionaryWordReader, WordAggregator>>();
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    for (auto & oi : _fusion_out_index.get_old_indexes()) {
        auto reader(std::make_unique<DictionaryWordReader>());
        const vespalib::string &tmpindexpath = createTmpPath(_field_dir, oi.getIndex());
        const vespalib::string &oldindexpath = oi.getPath();
        vespalib::string wordMapName = tmpindexpath + "/old2new.dat";
        vespalib::string fieldDir(oldindexpath + "/" + _field_name);
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
            _word_readers.push_back(std::move(reader));
            _word_heap->initialAdd(_word_readers.back().get());
        }
    }
    return true;
}

bool
FieldMerger::read_mapping_files()
{
    _word_num_mappings.resize(_fusion_out_index.get_old_indexes().size());
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    for (const auto & oi : _fusion_out_index.get_old_indexes()) {
        std::vector<uint32_t> oldIndexes;
        const Schema &oldSchema = oi.getSchema();
        if (!SchemaUtil::getIndexIds(oldSchema, DataType::STRING, oldIndexes)) {
            return false;
        }
        WordNumMapping &wordNumMapping = _word_num_mappings[oi.getIndex()];
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
FieldMerger::renumber_word_ids_start()
{
    LOG(debug, "Renumber word IDs for field %s", _field_name.c_str());
    if (!open_input_word_readers()) {
        return false;
    }
    _word_aggregator = std::make_unique<WordAggregator>();
    return true;
}

bool
FieldMerger::renumber_word_ids_main()
{
    _word_heap->merge(*_word_aggregator, 4, *_flush_token);
    if (_flush_token->stop_requested()) {
        return false;
    }
    assert(_word_heap->empty());
    return true;
}

bool
FieldMerger::renumber_word_ids_finish()
{
    _word_heap.reset();
    _num_word_ids = _word_aggregator->getWordNum();
    _word_aggregator.reset();

    // Close files
    for (auto &i : _word_readers) {
        i->close();
    }
    _word_readers.clear();

    // Now read mapping files back into an array
    // XXX: avoid this, and instead make the array here
    if (!read_mapping_files()) {
        return false;
    }
    LOG(debug, "Finished renumbering words IDs for field %s", _field_name.c_str());

    return true;
}

void
FieldMerger::renumber_word_ids_failed()
{
    _failed = true;
    if (_flush_token->stop_requested()) {
        return;
    }
    LOG(error, "Could not renumber field word ids for field %s dir %s", _field_name.c_str(), _field_dir.c_str());
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
FieldMerger::open_input_field_readers()
{
    _readers.reserve(_fusion_out_index.get_old_indexes().size());
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    auto field_length_scanner = allocate_field_length_scanner();
    for (const auto &oi : _fusion_out_index.get_old_indexes()) {
        const Schema &oldSchema = oi.getSchema();
        if (!index.hasOldFields(oldSchema)) {
            continue; // drop data
        }
        auto reader = FieldReader::allocFieldReader(index, oldSchema, field_length_scanner);
        reader->setup(_word_num_mappings[oi.getIndex()], oi.getDocIdMapping());
        if (!reader->open(oi.getPath() + "/" + _field_name + "/", _fusion_out_index.get_tune_file_indexing()._read)) {
            return false;
        }
        _readers.push_back(std::move(reader));
    }
    return true;
}

bool
FieldMerger::open_field_writer()
{
    FieldLengthInfo field_length_info;
    if (!_readers.empty()) {
        field_length_info = _readers.back()->get_field_length_info();
    }
    SchemaUtil::IndexIterator index(_fusion_out_index.get_schema(), _id);
    if (!_writer->open(_field_dir + "/", 64, 262144, _fusion_out_index.get_dynamic_k_pos_index_format(),
                       index.use_interleaved_features(), index.getSchema(),
                       index.getIndex(),
                       field_length_info,
                       _fusion_out_index.get_tune_file_indexing()._write, _fusion_out_index.get_file_header_context())) {
        throw IllegalArgumentException(make_string("Could not open output posocc + dictionary in %s", _field_dir.c_str()));
    }
    return true;
}

bool
FieldMerger::select_cooked_or_raw_features(FieldReader& reader)
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
        _writer->getFeatureParams(featureParams);
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
FieldMerger::setup_merge_heap()
{
    _heap = std::make_unique<PostingPriorityQueueMerger<FieldReader, FieldWriter>>();
    for (auto &reader : _readers) {
        if (!select_cooked_or_raw_features(*reader)) {
            return false;
        }
        if (reader->isValid()) {
            reader->read();
        }
        if (reader->isValid()) {
            _heap->initialAdd(reader.get());
        }
    }
    return true;
}

bool
FieldMerger::merge_postings_start()
{
    /* OUTPUT */
    _writer = std::make_unique<FieldWriter>(_fusion_out_index.get_doc_id_limit(), _num_word_ids);

    if (!open_input_field_readers()) {
        return false;
    }
    if (!open_field_writer()) {
        return false;
    }
    return setup_merge_heap();
}

bool
FieldMerger::merge_postings_main()
{
    _heap->merge(*_writer, 4, *_flush_token);
    if (_flush_token->stop_requested()) {
        return false;
    }
    assert(_heap->empty());
    return true;
}

bool
FieldMerger::merge_postings_finish()
{
    _heap.reset();

    for (auto &reader : _readers) {
        if (!reader->close()) {
            return false;
        }
    }
    _readers.clear();
    if (!_writer->close()) {
        throw IllegalArgumentException(make_string("Could not close output posocc + dictionary in %s", _field_dir.c_str()));
    }
    _writer.reset();
    return true;
}

void
FieldMerger::merge_postings_failed()
{
    _failed = true;
    if (_flush_token->stop_requested()) {
        return;
    }
    throw IllegalArgumentException(make_string("Could not merge field postings for field %s dir %s",
                                               _field_name.c_str(), _field_dir.c_str()));
}

void
FieldMerger::merge_field_start()
{
    const Schema &schema = _fusion_out_index.get_schema();
    SchemaUtil::IndexIterator index(schema, _id);
    SchemaUtil::IndexSettings settings = index.getIndexSettings();
    if (settings.hasError()) {
        _failed = true;
        return;
    }

    if (FileKit::hasStamp(_field_dir + "/.mergeocc_done")) {
        _state = State::MERGE_DONE;
        return;
    }
    vespalib::mkdir(_field_dir, false);

    LOG(debug, "merge_field for field %s dir %s", _field_name.c_str(), _field_dir.c_str());

    make_tmp_dirs();

    if (!renumber_word_ids_start()) {
        renumber_word_ids_failed();
        return;
    }
    _state = State::RENUMBER_WORD_IDS;
}

void
FieldMerger::merge_field_finish()
{
    bool res = merge_postings_finish();
    if (!res) {
        merge_postings_failed();
        _failed = true;
        return;
    }
    if (!FileKit::createStamp(_field_dir +  "/.mergeocc_done")) {
        _failed = true;
        return;
    }
    vespalib::File::sync(_field_dir);

    if (!clean_tmp_dirs()) {
        _failed = true;
        return;
    }

    LOG(debug, "Finished merge_field for field %s dir %s", _field_name.c_str(), _field_dir.c_str());

    _state = State::MERGE_DONE;
}

void
FieldMerger::process_merge_field()
{
    switch (_state) {
    case State::MERGE_START:
        merge_field_start();
        break;
    case State::RENUMBER_WORD_IDS:
        if (!renumber_word_ids_main()) {
            renumber_word_ids_failed();
        } else {
            _state = State::RENUMBER_WORD_IDS_FINISH;
        }
        break;
    case State::RENUMBER_WORD_IDS_FINISH:
        if (!renumber_word_ids_finish()) {
            renumber_word_ids_failed();
        } else if (!merge_postings_start()) {
            merge_postings_failed();
        } else {
            _state = State::MERGE_POSTINGS;
        }
        break;
    case State::MERGE_POSTINGS:
        if (!merge_postings_main()) {
            merge_postings_failed();
        } else {
            _state = State::MERGE_POSTINGS_FINISH;
        }
        break;
    case State::MERGE_POSTINGS_FINISH:
        merge_field_finish();
        break;
    case State::MERGE_DONE:
    default:
        LOG_ABORT("should not be reached");
    }
}

bool
FieldMerger::merge_field()
{
    while (!_failed && _state != State::MERGE_DONE) {
        process_merge_field();
    }
    return !_failed;
}

}
