// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fusion.h"
#include "fieldreader.h"
#include "dictionarywordreader.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/searchlib/common/documentsummary.h>
#include <vespa/vespalib/util/error.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".diskindex.fusion");

using search::FileKit;
using search::PostingPriorityQueue;
using search::common::FileHeaderContext;
using search::diskindex::DocIdMapping;
using search::diskindex::WordNumMapping;
using search::docsummary::DocumentSummary;
using search::index::PostingListParams;
using search::index::Schema;
using search::index::SchemaUtil;
using search::index::schema::DataType;
using vespalib::getLastErrorString;


namespace search {

namespace diskindex {

void
FusionInputIndex::setSchema(const Schema::SP &schema)
{
    _schema = schema;
}

Fusion::Fusion(bool dynamicKPosIndexFormat,
               const TuneFileIndexing &tuneFileIndexing,
               const FileHeaderContext &fileHeaderContext)
    : _schema(NULL),
      _oldIndexes(),
      _docIdLimit(0u),
      _numWordIds(0u),
      _dynamicKPosIndexFormat(dynamicKPosIndexFormat),
      _outDir("merged"),
      _tuneFileIndexing(tuneFileIndexing),
      _fileHeaderContext(fileHeaderContext)
{ }


Fusion::~Fusion()
{
    ReleaseMappingTables();
}


void
Fusion::setSchema(const Schema *schema)
{
    _schema = schema;
}


void
Fusion::setOutDir(const vespalib::string &outDir)
{
    _outDir = outDir;
}


void
Fusion::SetOldIndexList(const std::vector<vespalib::string> &oldIndexList)
{
    _oldIndexes.resize(oldIndexList.size());
    OldIndexIterator oldIndexIt = _oldIndexes.begin();
    uint32_t i = 0;
    for (std::vector<vespalib::string>::const_iterator
             it = oldIndexList.begin(), ite = oldIndexList.end();
         it != ite;
         ++it, ++oldIndexIt, ++i) {
        oldIndexIt->reset(allocOldIndex());
        OldIndex &oi = **oldIndexIt;
        oi.setPath(*it);
        std::ostringstream tmpindexpath0;
        tmpindexpath0 << _outDir;
        tmpindexpath0 << "/tmpindex";
        tmpindexpath0 << i;
        oi.setTmpPath(tmpindexpath0.str());
    }
}


bool
Fusion::openInputWordReaders(const SchemaUtil::IndexIterator &index,
                             std::vector<
                                 std::unique_ptr<DictionaryWordReader> > &
                             readers,
                             PostingPriorityQueue<DictionaryWordReader> &heap)
{
    for (auto &i : getOldIndexes()) {
        OldIndex &oi = *i;
        auto reader(std::make_unique<DictionaryWordReader>());
        const vespalib::string &tmpindexpath = oi.getTmpPath();
        const vespalib::string &oldindexpath = oi.getPath();
        vespalib::string wordMapName = tmpindexpath + "/old2new.dat";
        vespalib::string fieldDir(oldindexpath + "/" + index.getName());
        vespalib::string dictName(fieldDir + "/dictionary");
        const Schema &oldSchema = oi.getSchema();
        if (!index.hasOldFields(oldSchema, false)) {
            continue; // drop data
        }
        bool res = reader->open(dictName,
                                wordMapName,
                                _tuneFileIndexing._read);
        if (!res) {
            LOG(error, "Could not open dictionary %s to generate %s",
                dictName.c_str(), wordMapName.c_str());
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
Fusion::renumberFieldWordIds(const SchemaUtil::IndexIterator &index)
{
    vespalib::string indexName = index.getName();
    LOG(debug, "Renumber word IDs for field %s", indexName.c_str());

    std::vector<std::unique_ptr<DictionaryWordReader>> readers;
    PostingPriorityQueue<DictionaryWordReader> heap;
    WordAggregator out;

    if (!openInputWordReaders(index, readers, heap))
        return false;


    heap.merge(out, 4);
    assert(heap.empty());
    _numWordIds = out.getWordNum();

    // Close files
    for (auto &i : readers) {
        i->close();
    }

    // Now read mapping files back into an array
    // XXX: avoid this, and instead make the array here
    if (!ReadMappingFiles(&index))
        return false;

    LOG(debug, "Finished renumbering words IDs for field %s",
        indexName.c_str());

    return true;
}


bool
Fusion::mergeFields()
{
   typedef SchemaUtil::IndexIterator IndexIterator;

    const Schema &schema = getSchema();
    for (IndexIterator index(schema); index.isValid(); ++index) {
        if (!mergeField(index.getIndex()))
            return false;
    }
    return true;
}


bool
Fusion::mergeField(uint32_t id)
{
    typedef SchemaUtil::IndexIterator IndexIterator;
    typedef SchemaUtil::IndexSettings IndexSettings;

    const Schema &schema = getSchema();
    IndexIterator index(schema, id);
    const vespalib::string &indexName = index.getName();
    IndexSettings settings = index.getIndexSettings();
    if (settings.hasError())
        return false;
    vespalib::string indexDir = _outDir + "/" + indexName;

    if (FileKit::hasStamp(indexDir + "/.mergeocc_done"))
        return true;

    vespalib::mkdir(indexDir.c_str(), false);

    LOG(debug, "mergeField for field %s dir %s",
        indexName.c_str(), indexDir.c_str());

    makeTmpDirs();

    if (!renumberFieldWordIds(index)) {
        LOG(error, "Could not renumber field word ids for field %s dir %s",
            indexName.c_str(), indexDir.c_str());
        return false;
    }

    // Tokamak
    bool res = mergeFieldPostings(index);
    if (!res) {
        LOG(error, "Could not merge field postings for field %s dir %s",
            indexName.c_str(), indexDir.c_str());
        LOG_ABORT("should not be reached");
    }
    if (!FileKit::createStamp(indexDir +  "/.mergeocc_done"))
        return false;

    if (!CleanTmpDirs())
        return false;

    LOG(debug, "Finished mergeField for field %s dir %s",
        indexName.c_str(), indexDir.c_str());

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

    if (!reader.isValid())
        return true;
    {
        writer.getFeatureParams(featureParams);
        cookedFormat = featureParams.getStr("cookedEncoding");
        rawFormat = featureParams.getStr("encoding");
        if (rawFormat == "")
            rawFormatOK = false;    // Typically uncompressed file
        outFeatureParams = featureParams;
    }
    {
        reader.getFeatureParams(featureParams);
        if (cookedFormat != featureParams.getStr("cookedEncoding"))
            cookedFormatOK = false;
        if (rawFormat != featureParams.getStr("encoding"))
            rawFormatOK = false;
        if (featureParams != outFeatureParams)
            rawFormatOK = false;
        if (!reader.allowRawFeatures())
            rawFormatOK = false;    // Reader transforms data
    }
    if (!cookedFormatOK) {
        LOG(error,
            "Cannot perform fusion, cooked feature formats don't match");
        return false;
    }
    if (rawFormatOK) {
        featureParams.clear();
        featureParams.set("cooked", false);
        reader.setFeatureParams(featureParams);
        reader.getFeatureParams(featureParams);
        if (featureParams.isSet("cookedEncoding") ||
            rawFormat != featureParams.getStr("encoding"))
            rawFormatOK = false;
        if (!rawFormatOK) {
            LOG(error, "Cannot perform fusion, raw format setting failed");
            return false;
        }
        LOG(debug, "Using raw feature format for fusion of posting files");
    }
    return true;
}


bool
Fusion::openInputFieldReaders(const SchemaUtil::IndexIterator &index,
                              std::vector<std::unique_ptr<FieldReader> > &
                              readers)
{
    vespalib::string indexName = index.getName();
    for (auto &i : _oldIndexes) {
        OldIndex &oi = *i;
        const Schema &oldSchema = oi.getSchema();
        if (!index.hasOldFields(oldSchema, false)) {
            continue; // drop data
        }
        auto reader = FieldReader::allocFieldReader(index, oldSchema);
        reader->setup(oi.getWordNumMapping(),
                      oi.getDocIdMapping());
        if (!reader->open(oi.getPath() + "/" +
                          indexName + "/",
                          _tuneFileIndexing._read))
            return false;
        readers.push_back(std::move(reader));
    }
    return true;
}


bool
Fusion::openFieldWriter(const SchemaUtil::IndexIterator &index,
                        FieldWriter &writer)
{
    vespalib::string dir = _outDir + "/" + index.getName();

    if (!writer.open(dir + "/",
                     64,
                     262144,
                     _dynamicKPosIndexFormat,
                     index.getSchema(),
                     index.getIndex(),
                     _tuneFileIndexing._write,
                     _fileHeaderContext)) {
        LOG(error, "Could not open output posocc + dictionary in %s",
            dir.c_str());
        LOG_ABORT("should not be reached");
        return false;
    }
    return true;
}


bool
Fusion::setupMergeHeap(const std::vector<std::unique_ptr<FieldReader> > &
                       readers,
                       FieldWriter &writer,
                       PostingPriorityQueue<FieldReader> &heap)
{
    for (auto &reader : readers) {
        if (!selectCookedOrRawFeatures(*reader, writer))
            return false;
        if (reader->isValid())
            reader->read();
        if (reader->isValid())
            heap.initialAdd(reader.get());
    }
    return true;
}


bool
Fusion::mergeFieldPostings(const SchemaUtil::IndexIterator &index)
{
    std::vector<std::unique_ptr<FieldReader>> readers;
    PostingPriorityQueue<FieldReader> heap;
    /* OUTPUT */
    FieldWriter fieldWriter(_docIdLimit, _numWordIds);
    vespalib::string indexName = index.getName();

    if (!openInputFieldReaders(index, readers))
        return false;
    if (!openFieldWriter(index, fieldWriter))
        return false;
    if (!setupMergeHeap(readers, fieldWriter, heap))
        return false;

    heap.merge(fieldWriter, 4);
    assert(heap.empty());

    for (auto &reader : readers) {
        if (!reader->close())
            return false;
    }
    if (!fieldWriter.close()) {
        LOG(error, "Could not close output posocc + dictionary in %s/%s",
            _outDir.c_str(), indexName.c_str());
        LOG_ABORT("should not be reached");
    }
    return true;
}


bool
Fusion::ReadMappingFiles(const SchemaUtil::IndexIterator *index)
{
    ReleaseMappingTables();

    size_t numberOfOldIndexes = _oldIndexes.size();
    for (uint32_t i = 0; i < numberOfOldIndexes; i++)
    {
        OldIndex &oi = *_oldIndexes[i];
        WordNumMapping &wordNumMapping = oi.getWordNumMapping();
        std::vector<uint32_t> oldIndexes;
        const Schema &oldSchema = oi.getSchema();
        if (!SchemaUtil::getIndexIds(oldSchema,
                                     DataType::STRING,
                                     oldIndexes))
            return false;
        if (oldIndexes.empty()) {
            wordNumMapping.noMappingFile();
            continue;
        }
        if (index && !index->hasOldFields(oldSchema, false)) {
            continue; // drop data
        }

        // Open word mapping file
        vespalib::string old2newname = oi.getTmpPath() + "/old2new.dat";
        wordNumMapping.readMappingFile(old2newname, _tuneFileIndexing._read);
    }

    return true;
}


bool
Fusion::ReleaseMappingTables()
{
    size_t numberOfOldIndexes = _oldIndexes.size();
    for (uint32_t i = 0; i < numberOfOldIndexes; i++)
    {
        OldIndex &oi = *_oldIndexes[i];
        oi.getWordNumMapping().clear();
    }
    return true;
}


void
Fusion::makeTmpDirs()
{
    for (auto &i : getOldIndexes()) {
        OldIndex &oi = *i;
        // Make tmpindex directories
        const vespalib::string &tmpindexpath = oi.getTmpPath();
        vespalib::mkdir(tmpindexpath, false);
    }
}

bool
Fusion::CleanTmpDirs()
{
    uint32_t i = 0;
    for (;;) {
        std::ostringstream tmpindexpath0;
        tmpindexpath0 << _outDir;
        tmpindexpath0 << "/tmpindex";
        tmpindexpath0 << i;
        const vespalib::string &tmpindexpath = tmpindexpath0.str();
        FastOS_StatInfo statInfo;
        if (!FastOS_File::Stat(tmpindexpath.c_str(), &statInfo)) {
            if (statInfo._error == FastOS_StatInfo::FileNotFound)
                break;
            LOG(error, "Failed to stat tmpdir %s", tmpindexpath.c_str());
            return false;
        }
        i++;
    }
    while (i > 0) {
        i--;
        // Remove tmpindex directories
        std::ostringstream tmpindexpath0;
        tmpindexpath0 << _outDir;
        tmpindexpath0 << "/tmpindex";
        tmpindexpath0 << i;
        const vespalib::string &tmpindexpath = tmpindexpath0.str();
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
    return true;
}


bool
Fusion::readSchemaFiles()
{
    OldIndexIterator oldIndexIt = _oldIndexes.begin();
    OldIndexIterator oldIndexIte = _oldIndexes.end();

    for(; oldIndexIt != oldIndexIte; ++oldIndexIt) {
        OldIndex &oi = **oldIndexIt;
        vespalib::string oldcfname = oi.getPath() + "/schema.txt";
        Schema::SP schema(new Schema);
        if (!schema->loadFromFile(oldcfname))
            return false;
        if (!SchemaUtil::validateSchema(*_schema))
            return false;
        oi.setSchema(schema);
    }

    /* TODO: Check compatibility */
    bool res = checkSchemaCompat();
    if (!res)
        LOG(error, "Index fusion cannot continue due to incompatible indexes");
    return res;
}


bool
Fusion::merge(const Schema &schema,
              const vespalib::string &dir,
              const std::vector<vespalib::string> &sources,
              const SelectorArray &selector,
              bool dynamicKPosOccFormat,
              const TuneFileIndexing &tuneFileIndexing,
              const FileHeaderContext &fileHeaderContext)
{
    assert(sources.size() <= 255);
    uint32_t docIdLimit = selector.size();
    uint32_t trimmedDocIdLimit = docIdLimit;

    // Limit docIdLimit in output based on selections that cannot be satisfied
    uint32_t sourcesSize = sources.size();
    while (trimmedDocIdLimit > 0 &&
           selector[trimmedDocIdLimit - 1] >= sourcesSize)
        --trimmedDocIdLimit;

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
        LOG(error, "Could not write docsum count in dir %s: %s",
            dir.c_str(), getLastErrorString().c_str());
        return false;
    }

    std::unique_ptr<Fusion> fusion(new Fusion(dynamicKPosOccFormat,
                                         tuneFileIndexing,
                                         fileHeaderContext));
    fusion->setSchema(&schema);
    fusion->setOutDir(dir);
    fusion->SetOldIndexList(sources);
    if (!fusion->readSchemaFiles()) {
        LOG(error, "Cannot read schema files for source indexes");
        return false;
    }
    uint32_t idx = 0;
    std::vector<std::shared_ptr<OldIndex> > &oldIndexes =
        fusion->getOldIndexes();

    for (OldIndexIterator i = oldIndexes.begin(), ie = oldIndexes.end();
         i != ie; ++i, ++idx) {
        OldIndex &oi = **i;
        // Make tmpindex directories
        const vespalib::string &tmpindexpath = oi.getTmpPath();
        vespalib::mkdir(tmpindexpath, false);
        DocIdMapping &docIdMapping = oi.getDocIdMapping();
        if (!docIdMapping.readDocIdLimit(oi.getPath())) {
            LOG(error, "Cannot determine docIdLimit for old index \"%s\"",
                oi.getPath().c_str());
            return false;
        }
        docIdMapping.setup(docIdMapping._docIdLimit,
                           &selector,
                           idx);
    }
    fusion->setDocIdLimit(trimmedDocIdLimit);
    if (!fusion->mergeFields())
        return false;
    return true;
}


} // namespace diskindex

} // namespace search
