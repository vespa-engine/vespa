// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docidmapper.h"
#include "wordnummapper.h"

#include <vespa/searchlib/index/schemautil.h>
#include <vector>
#include <string>

namespace search { template <class IN> class PostingPriorityQueue; }

namespace search::common {
    class TuneFileIndexing;
    class FileHeaderContext;
}

namespace search::diskindex {

class FieldReader;
class FieldWriter;
class DictionaryWordReader;

class FusionInputIndex
{
public:
    typedef diskindex::WordNumMapping WordNumMapping;
    typedef diskindex::DocIdMapping DocIdMapping;
private:
    vespalib::string _path;
    WordNumMapping _wordNumMapping;
    DocIdMapping _docIdMapping;
    vespalib::string _tmpPath;
    index::Schema::SP _schema;

public:
    FusionInputIndex()
        : _path(),
          _wordNumMapping(),
          _docIdMapping(),
          _tmpPath(),
          _schema()
    {
    }

    virtual ~FusionInputIndex() {}

    void setPath(const vespalib::string &path) { _path = path; }
    const vespalib::string & getPath() const { return _path; }
    void setTmpPath(const vespalib::string &tmpPath) { _tmpPath = tmpPath; }
    const vespalib::string &getTmpPath() const { return _tmpPath; }
    const WordNumMapping & getWordNumMapping() const { return _wordNumMapping; }
    WordNumMapping & getWordNumMapping() { return _wordNumMapping; }
    const DocIdMapping & getDocIdMapping() const { return _docIdMapping; }

    DocIdMapping & getDocIdMapping() { return _docIdMapping; }

    const index::Schema &getSchema() const {
        assert(_schema);
        return *_schema;
    }

    void setSchema(const index::Schema::SP &schema);
};


class Fusion
{
public:
    typedef search::index::Schema Schema;
    typedef search::index::SchemaUtil SchemaUtil;

private:
    Fusion(const Fusion &);
    Fusion& operator=(const Fusion &);

public:
    Fusion(bool dynamicKPosIndexFormat,
           const TuneFileIndexing &tuneFileIndexing,
           const search::common::FileHeaderContext &fileHeaderContext);

    virtual ~Fusion();

    void SetOldIndexList(const std::vector<vespalib::string> &oldIndexList);
    bool mergeFields();
    bool mergeField(uint32_t id);
    bool openInputFieldReaders(const SchemaUtil::IndexIterator &index,
                               std::vector<std::unique_ptr<FieldReader> > &
                               readers);
    bool openFieldWriter(const SchemaUtil::IndexIterator &index, FieldWriter & writer);
    bool setupMergeHeap(const std::vector<std::unique_ptr<FieldReader> > & readers,
                        FieldWriter &writer, PostingPriorityQueue<FieldReader> &heap);
    bool mergeFieldPostings(const SchemaUtil::IndexIterator &index);
    bool openInputWordReaders(const SchemaUtil::IndexIterator &index,
                              std::vector<std::unique_ptr<DictionaryWordReader> > &readers,
                              PostingPriorityQueue<DictionaryWordReader> &heap);
    bool renumberFieldWordIds(const SchemaUtil::IndexIterator &index);
    void setSchema(const Schema *schema);
    void setOutDir(const vespalib::string &outDir);
    void makeTmpDirs();
    bool CleanTmpDirs();
    bool readSchemaFiles();
    bool checkSchemaCompat();

    template <class Reader, class Writer>
    static bool
    selectCookedOrRawFeatures(Reader &reader, Writer &writer);

protected:
    bool ReadMappingFiles(const SchemaUtil::IndexIterator *index);
    bool ReleaseMappingTables();
protected:

    typedef FusionInputIndex OldIndex;

    const Schema *_schema;  // External ownership
    std::vector<std::shared_ptr<OldIndex> > _oldIndexes;
    typedef std::vector<std::shared_ptr<OldIndex> >::iterator
    OldIndexIterator;

    // OUTPUT:

    uint32_t _docIdLimit;
    uint64_t _numWordIds;

    // Index format parameters.
    bool _dynamicKPosIndexFormat;

    // Index location parameters

    /*
     * Output location
     */
    vespalib::string _outDir;

    const TuneFileIndexing &_tuneFileIndexing;
    const common::FileHeaderContext &_fileHeaderContext;

    const Schema &getSchema() const {
        assert(_schema != nullptr);
        return *_schema;
    }
public:

    void setDocIdLimit(uint32_t docIdLimit) { _docIdLimit = docIdLimit; }
    std::vector<std::shared_ptr<OldIndex> > & getOldIndexes() { return _oldIndexes; }
    virtual OldIndex *allocOldIndex() { return new OldIndex; }

    /**
     * This method is used by new indexing pipeline to merge indexes.
     */
    static bool merge(const Schema &schema,
                      const vespalib::string &dir,
                      const std::vector<vespalib::string> &sources,
                      const SelectorArray &docIdSelector,
                      bool dynamicKPosOccFormat,
                      const TuneFileIndexing &tuneFileIndexing,
                      const common::FileHeaderContext &fileHeaderContext);
};

}
