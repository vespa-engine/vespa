// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docidmapper.h"
#include "wordnummapper.h"

#include <vespa/searchlib/index/schemautil.h>
#include <vespa/vespalib/util/threadexecutor.h>

namespace search { template <class IN> class PostingPriorityQueue; }
namespace search {
class IFlushToken;
class TuneFileIndexing;
}
namespace search::common { class FileHeaderContext; }
namespace search::index { class FieldLengthInfo; }

namespace search::diskindex {

class FieldLengthScanner;
class FieldReader;
class FieldWriter;
class DictionaryWordReader;

class FusionInputIndex
{
private :
    vespalib::string  _path;
    uint32_t          _index;
    index::Schema     _schema;
    DocIdMapping      _docIdMapping;

public:
    FusionInputIndex(const vespalib::string &path, uint32_t index, const SelectorArray & selector);
    FusionInputIndex(FusionInputIndex &&) = default;
    FusionInputIndex & operator = (FusionInputIndex &&) = default;
    ~FusionInputIndex();

    const vespalib::string & getPath() const { return _path; }
    uint32_t getIndex() const { return _index; }
    const DocIdMapping & getDocIdMapping() const { return _docIdMapping; }
    const index::Schema &getSchema() const { return _schema; }
};


class Fusion
{
private:
    using Schema = index::Schema;
    using SchemaUtil = index::SchemaUtil;
    using WordNumMappingList = std::vector<WordNumMapping>;

    bool mergeFields(vespalib::ThreadExecutor & executor, std::shared_ptr<IFlushToken> flush_token);
    bool mergeField(uint32_t id, std::shared_ptr<IFlushToken> flush_token);
    std::shared_ptr<FieldLengthScanner> allocate_field_length_scanner(const SchemaUtil::IndexIterator &index);
    bool openInputFieldReaders(const SchemaUtil::IndexIterator &index, const WordNumMappingList & list,
                               std::vector<std::unique_ptr<FieldReader> > & readers);
    bool openFieldWriter(const SchemaUtil::IndexIterator &index, FieldWriter & writer, const index::FieldLengthInfo &field_length_info);
    bool setupMergeHeap(const std::vector<std::unique_ptr<FieldReader> > & readers,
                        FieldWriter &writer, PostingPriorityQueue<FieldReader> &heap);
    bool mergeFieldPostings(const SchemaUtil::IndexIterator &index, const WordNumMappingList & list, uint64_t numWordIds, const IFlushToken& flush_token);
    bool openInputWordReaders(const vespalib::string & dir, const SchemaUtil::IndexIterator &index,
                              std::vector<std::unique_ptr<DictionaryWordReader> > &readers,
                              PostingPriorityQueue<DictionaryWordReader> &heap);
    bool renumberFieldWordIds(const vespalib::string & dir, const SchemaUtil::IndexIterator &index,
                              WordNumMappingList & list, uint64_t& numWordIds, const IFlushToken& flush_token);
    void makeTmpDirs(const vespalib::string & dir);
    bool cleanTmpDirs(const vespalib::string & dir);
    bool readSchemaFiles();
    bool checkSchemaCompat();

    template <class Reader, class Writer>
    static bool selectCookedOrRawFeatures(Reader &reader, Writer &writer);

    bool readMappingFiles(const vespalib::string & dir, const SchemaUtil::IndexIterator *index, WordNumMappingList & list);
    const Schema &getSchema() const { return _schema; }

    const Schema     &_schema;  // External ownership
    std::vector<FusionInputIndex> _oldIndexes;
    const uint32_t    _docIdLimit;
    const bool        _dynamicKPosIndexFormat;
    vespalib::string  _outDir;

    const TuneFileIndexing          &_tuneFileIndexing;
    const common::FileHeaderContext &_fileHeaderContext;
public:
    Fusion(const Fusion &) = delete;
    Fusion& operator=(const Fusion &) = delete;
    Fusion(uint32_t docIdLimit, const Schema &schema, const vespalib::string &dir,
           const std::vector<vespalib::string> & sources, const SelectorArray &selector, bool dynamicKPosIndexFormat,
           const TuneFileIndexing &tuneFileIndexing, const common::FileHeaderContext &fileHeaderContext);

    ~Fusion();

    static bool
    merge(const Schema &schema, const vespalib::string &dir, const std::vector<vespalib::string> &sources,
          const SelectorArray &docIdSelector, bool dynamicKPosOccFormat, const TuneFileIndexing &tuneFileIndexing,
          const common::FileHeaderContext &fileHeaderContext, vespalib::ThreadExecutor & executor,
          std::shared_ptr<IFlushToken> flush_token);
};

}
