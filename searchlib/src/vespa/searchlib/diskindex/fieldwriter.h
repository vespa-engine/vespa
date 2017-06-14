// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bitvectorfile.h"
#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/countcompression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>

namespace vespalib { class nbostream; }

namespace search {

namespace diskindex {

/*
 * FieldWriter is used to write a dictionary and posting list file
 * together.
 *
 * It is used by the fusion code to write the merged output for a field,
 * and by the memory index dump code to write a field to disk.
 */
class FieldWriter
{
private:
    FieldWriter(const FieldWriter &rhs) = delete;
    FieldWriter(const FieldWriter &&rhs) = delete;
    FieldWriter &operator=(const FieldWriter &rhs) = delete;
    FieldWriter &operator=(const FieldWriter &&rhs) = delete;

    uint64_t _wordNum;
    uint32_t _prevDocId;

    static uint64_t noWordNum() { return 0u; }
public:

    using DictionaryFileSeqWrite = index::DictionaryFileSeqWrite;

    typedef index::PostingListFileSeqWrite PostingListFileSeqWrite;
    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::Schema Schema;
    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListParams PostingListParams;

    std::unique_ptr<DictionaryFileSeqWrite> _dictFile;
    std::unique_ptr<PostingListFileSeqWrite> _posoccfile;
private:
    BitVectorCandidate _bvc;
    BitVectorFileWrite _bmapfile;
    uint32_t _docIdLimit;
    uint64_t _numWordIds;
    vespalib::string _prefix;
    uint64_t _compactWordNum;
    vespalib::string _word;

    void flush();

public:
    FieldWriter(uint32_t docIdLimit, uint64_t numWordIds);
    ~FieldWriter();

    void newWord(uint64_t wordNum, const vespalib::stringref &word);
    void newWord(const vespalib::stringref &word);

    void add(const DocIdAndFeatures &features) {
        assert(features._docId < _docIdLimit);
        assert(features._docId > _prevDocId);
        _posoccfile->writeDocIdAndFeatures(features);
        _bvc.add(features._docId);
        _prevDocId = features._docId;
    }

    uint64_t getSparseWordNum() const { return _wordNum; }

    void earlyOpen(const vespalib::string &prefix, uint32_t minSkipDocs, uint32_t minChunkDocs,
                   bool dynamicKPosOccFormat, const Schema &schema, uint32_t indexId,
                   const TuneFileSeqWrite &tuneFileWrite);

    bool lateOpen(const TuneFileSeqWrite &tuneFileWrite,
                  const search::common::FileHeaderContext &fileHeaderContext);

    bool close();

    /*
     * To be called between words, not in the middle of one.
     */
    void checkPointWrite(vespalib::nbostream &out);

    /*
     * To be called after earlyOpen() but before afterOpen().
     */
    void checkPointRead(vespalib::nbostream &in);
    void setFeatureParams(const PostingListParams &params);
    void getFeatureParams(PostingListParams &params);
    static void remove(const vespalib::string &prefix);
};

} // namespace diskindex

} // namespace search

