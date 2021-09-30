// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fusion.h"
#include "fieldreader.h"
#include "dictionarywordreader.h"
#include "field_length_scanner.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>
#include <vespa/searchlib/common/i_flush_token.h>
#include <vespa/searchlib/index/field_length_info.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/searchlib/util/postingpriorityqueue.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchlib/common/documentsummary.h>
#include <vespa/vespalib/util/error.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/document/util/queue.h>
#include <sstream>

#include <vespa/log/log.h>
#include <vespa/vespalib/util/exceptions.h>

LOG_SETUP(".diskindex.fusion");

using search::FileKit;
using search::PostingPriorityQueue;
using search::common::FileHeaderContext;
using search::diskindex::DocIdMapping;
using search::diskindex::WordNumMapping;
using search::docsummary::DocumentSummary;
using search::index::FieldLengthInfo;
using search::bitcompression::PosOccFieldParams;
using search::bitcompression::PosOccFieldsParams;
using search::index::PostingListParams;
using search::index::Schema;
using search::index::SchemaUtil;
using search::index::schema::DataType;
using vespalib::getLastErrorString;
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

FusionInputIndex::FusionInputIndex(const vespalib::string &path, uint32_t index, const SelectorArray &selector)
    : _path(path),
      _index(index),
      _schema()
{
    vespalib::string fname = path + "/schema.txt";
    if ( ! _schema.loadFromFile(fname)) {
        throw IllegalArgumentException(make_string("Failed loading schema %s", fname.c_str()));
    }
    if ( ! SchemaUtil::validateSchema(_schema)) {
        throw IllegalArgumentException(make_string("Failed validating schema %s", fname.c_str()));
    }
    if (!_docIdMapping.readDocIdLimit(path)) {
        throw IllegalArgumentException(make_string("Cannot determine docIdLimit for old index \"%s\"", path.c_str()));
    }
    _docIdMapping.setup(_docIdMapping._docIdLimit, &selector, index);
}

FusionInputIndex::~FusionInputIndex() = default;

Fusion::Fusion(uint32_t docIdLimit, const Schema & schema, const vespalib::string & dir,
               const std::vector<vespalib::string> & sources, const SelectorArray &selector,
               bool dynamicKPosIndexFormat, const TuneFileIndexing &tuneFileIndexing,
               const FileHeaderContext &fileHeaderContext)
    : _schema(schema),
      _oldIndexes(createInputIndexes(sources, selector)),
      _docIdLimit(docIdLimit),
      _dynamicKPosIndexFormat(dynamicKPosIndexFormat),
      _outDir(dir),
      _tuneFileIndexing(tuneFileIndexing),
      _fileHeaderContext(fileHeaderContext)
{
    if (!readSchemaFiles()) {
        throw IllegalArgumentException("Cannot read schema files for source indexes");
    }
}

Fusion::~Fusion() = default;

bool
Fusion::openInputWordReaders(const vespalib::string & dir, const SchemaUtil::IndexIterator &index,
                             std::vector<std::unique_ptr<DictionaryWordReader> > & readers,
                             PostingPriorityQueue<DictionaryWordReader> &heap)
{
    for (auto & oi : _oldIndexes) {
        auto reader(std::make_unique<DictionaryWordReader>());
        const vespalib::string &tmpindexpath = createTmpPath(dir, oi.getIndex());
        const vespalib::string &oldindexpath = oi.getPath();
        vespalib::string wordMapName = tmpindexpath + "/old2new.dat";
        vespalib::string fieldDir(oldindexpath + "/" + index.getName());
        vespalib::string dictName(fieldDir + "/dictionary");
        const Schema &oldSchema = oi.getSchema();
        if (!index.hasOldFields(oldSchema)) {
            continue; // drop data
        }
        bool res = reader->open(dictName, wordMapName, _tuneFileIndexing._read);
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
Fusion::renumberFieldWordIds(const vespalib::string & dir, const SchemaUtil::IndexIterator &index,
                             WordNumMappingList & list, uint64_t &  numWordIds, const IFlushToken& flush_token)
{
    vespalib::string indexName = index.getName();
    LOG(debug, "Renumber word IDs for field %s", indexName.c_str());

    std::vector<std::unique_ptr<DictionaryWordReader>> readers;
    PostingPriorityQueue<DictionaryWordReader> heap;
    WordAggregator out;

    if (!openInputWordReaders(dir, index, readers, heap)) {
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
    if (!readMappingFiles(dir, &index, list)) {
        return false;
    }
    LOG(debug, "Finished renumbering words IDs for field %s", indexName.c_str());

    return true;
}


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
            if (!mergeField(index, flush_token)) {
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
Fusion::mergeField(uint32_t id, std::shared_ptr<IFlushToken> flush_token)
{
    typedef SchemaUtil::IndexIterator IndexIterator;
    typedef SchemaUtil::IndexSettings IndexSettings;

    const Schema &schema = getSchema();
    IndexIterator index(schema, id);
    const vespalib::string &indexName = index.getName();
    IndexSettings settings = index.getIndexSettings();
    if (settings.hasError()) {
        return false;
    }
    vespalib::string indexDir = _outDir + "/" + indexName;

    if (FileKit::hasStamp(indexDir + "/.mergeocc_done")) {
        return true;
    }
    vespalib::mkdir(indexDir, false);

    LOG(debug, "mergeField for field %s dir %s", indexName.c_str(), indexDir.c_str());

    makeTmpDirs(indexDir);

    WordNumMappingList list(_oldIndexes.size());
    uint64_t numWordIds(0);
    if (!renumberFieldWordIds(indexDir, index, list, numWordIds, *flush_token)) {
        if (flush_token->stop_requested()) {
            return false;
        }
        LOG(error, "Could not renumber field word ids for field %s dir %s", indexName.c_str(), indexDir.c_str());
        return false;
    }

    // Tokamak
    bool res = mergeFieldPostings(index, list, numWordIds, *flush_token);
    if (!res) {
        if (flush_token->stop_requested()) {
            return false;
        }
        throw IllegalArgumentException(make_string("Could not merge field postings for field %s dir %s",
                                                   indexName.c_str(), indexDir.c_str()));
    }
    if (!FileKit::createStamp(indexDir +  "/.mergeocc_done")) {
        return false;
    }
    vespalib::File::sync(indexDir);

    if (!cleanTmpDirs(indexDir)) {
        return false;
    }

    LOG(debug, "Finished mergeField for field %s dir %s", indexName.c_str(), indexDir.c_str());

    return true;
}

template <class Reader, class Writer>
bool
Fusion::selectCookedOrRawFeatures(Reader &reader, Writer &writer)
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


std::shared_ptr<FieldLengthScanner>
Fusion::allocate_field_length_scanner(const SchemaUtil::IndexIterator &index)
{
    if (index.use_interleaved_features()) {
        PosOccFieldsParams fieldsParams;
        fieldsParams.setSchemaParams(index.getSchema(), index.getIndex());
        assert(fieldsParams.getNumFields() > 0);
        const PosOccFieldParams &fieldParams = fieldsParams.getFieldParams()[0];
        if (fieldParams._hasElements) {
            for (const auto &old_index : _oldIndexes) {
                const Schema &old_schema = old_index.getSchema();
                if (index.hasOldFields(old_schema) &&
                    !index.has_matching_use_interleaved_features(old_schema)) {
                    return std::make_shared<FieldLengthScanner>(_docIdLimit);
                }
            }
        }
    }
    return std::shared_ptr<FieldLengthScanner>();
}

bool
Fusion::openInputFieldReaders(const SchemaUtil::IndexIterator &index, const WordNumMappingList & list,
                              std::vector<std::unique_ptr<FieldReader> > & readers)
{
    auto field_length_scanner = allocate_field_length_scanner(index);
    vespalib::string indexName = index.getName();
    for (const auto &oi : _oldIndexes) {
        const Schema &oldSchema = oi.getSchema();
        if (!index.hasOldFields(oldSchema)) {
            continue; // drop data
        }
        auto reader = FieldReader::allocFieldReader(index, oldSchema, field_length_scanner);
        reader->setup(list[oi.getIndex()], oi.getDocIdMapping());
        if (!reader->open(oi.getPath() + "/" + indexName + "/", _tuneFileIndexing._read)) {
            return false;
        }
        readers.push_back(std::move(reader));
    }
    return true;
}


bool
Fusion::openFieldWriter(const SchemaUtil::IndexIterator &index, FieldWriter &writer, const FieldLengthInfo &field_length_info)
{
    vespalib::string dir = _outDir + "/" + index.getName();

    if (!writer.open(dir + "/", 64, 262144, _dynamicKPosIndexFormat,
                     index.use_interleaved_features(), index.getSchema(),
                     index.getIndex(),
                     field_length_info,
                     _tuneFileIndexing._write, _fileHeaderContext)) {
        throw IllegalArgumentException(make_string("Could not open output posocc + dictionary in %s", dir.c_str()));
    }
    return true;
}


bool
Fusion::setupMergeHeap(const std::vector<std::unique_ptr<FieldReader> > & readers,
                       FieldWriter &writer, PostingPriorityQueue<FieldReader> &heap)
{
    for (auto &reader : readers) {
        if (!selectCookedOrRawFeatures(*reader, writer)) {
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
Fusion::mergeFieldPostings(const SchemaUtil::IndexIterator &index, const WordNumMappingList & list, uint64_t numWordIds, const IFlushToken& flush_token)
{
    std::vector<std::unique_ptr<FieldReader>> readers;
    PostingPriorityQueue<FieldReader> heap;
    /* OUTPUT */
    FieldWriter fieldWriter(_docIdLimit, numWordIds);
    vespalib::string indexName = index.getName();

    if (!openInputFieldReaders(index, list, readers)) {
        return false;
    }
    FieldLengthInfo field_length_info;
    if (!readers.empty()) {
        field_length_info = readers.back()->get_field_length_info();
    }
    if (!openFieldWriter(index, fieldWriter, field_length_info)) {
        return false;
    }
    if (!setupMergeHeap(readers, fieldWriter, heap)) {
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
        throw IllegalArgumentException(make_string("Could not close output posocc + dictionary in %s/%s",
                                                   _outDir.c_str(), indexName.c_str()));
    }
    return true;
}


bool
Fusion::readMappingFiles(const vespalib::string & dir, const SchemaUtil::IndexIterator *index, WordNumMappingList & list)
{
    for (const auto & oi : _oldIndexes) {
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
        if (index && !index->hasOldFields(oldSchema)) {
            continue; // drop data
        }

        // Open word mapping file
        vespalib::string old2newname = createTmpPath(dir, oi.getIndex()) + "/old2new.dat";
        wordNumMapping.readMappingFile(old2newname, _tuneFileIndexing._read);
    }

    return true;
}


void
Fusion::makeTmpDirs(const vespalib::string & dir)
{
    for (const auto & index : _oldIndexes) {
        vespalib::mkdir(createTmpPath(dir, index.getIndex()), false);
    }
}

bool
Fusion::cleanTmpDirs(const vespalib::string & dir)
{
    uint32_t i = 0;
    for (;;) {
        vespalib::string tmpindexpath = createTmpPath(dir, i);
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
        vespalib::string tmpindexpath = createTmpPath(dir, i);
        search::DirectoryTraverse dt(tmpindexpath.c_str());
        if (!dt.RemoveTree()) {
            LOG(error, "Failed to clean tmpdir %s", tmpindexpath.c_str());
            return false;
        }
    }
    return true;
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
