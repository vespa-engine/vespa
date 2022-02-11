// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bitvectorfile.h"
#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/bitcompression/countcompression.h>

namespace search::index { class Schema; }

namespace search::diskindex {

/**
 * FieldWriter is used to write a dictionary and posting list file together.
 *
 * It is used by the fusion code to write the merged output for a field,
 * and by the memory index dump code to write a field to disk.
 */
class FieldWriter {
private:
    uint64_t _wordNum;
    uint32_t _prevDocId;

    static uint64_t noWordNum() { return 0u; }
public:

    using DictionaryFileSeqWrite = index::DictionaryFileSeqWrite;

    using PostingListFileSeqWrite = index::PostingListFileSeqWrite;
    using DocIdAndFeatures = index::DocIdAndFeatures;
    using Schema = index::Schema;
    using PostingListCounts = index::PostingListCounts;
    using PostingListParams = index::PostingListParams;

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
    FieldWriter(const FieldWriter &rhs) = delete;
    FieldWriter(const FieldWriter &&rhs) = delete;
    FieldWriter &operator=(const FieldWriter &rhs) = delete;
    FieldWriter &operator=(const FieldWriter &&rhs) = delete;
    FieldWriter(uint32_t docIdLimit, uint64_t numWordIds);
    ~FieldWriter();

    void newWord(uint64_t wordNum, vespalib::stringref word);
    void newWord(vespalib::stringref word);

    void add(const DocIdAndFeatures &features) {
        assert(features.doc_id() < _docIdLimit);
        assert(features.doc_id() > _prevDocId);
        _posoccfile->writeDocIdAndFeatures(features);
        _bvc.add(features.doc_id());
        _prevDocId = features.doc_id();
    }

    uint64_t getSparseWordNum() const { return _wordNum; }

    bool open(const vespalib::string &prefix, uint32_t minSkipDocs, uint32_t minChunkDocs,
              bool dynamicKPosOccFormat,
              bool encode_interleaved_features,
              const Schema &schema, uint32_t indexId,
              const index::FieldLengthInfo &field_length_info,
              const TuneFileSeqWrite &tuneFileWrite,
              const search::common::FileHeaderContext &fileHeaderContext);

    bool close();

    void setFeatureParams(const PostingListParams &params);
    void getFeatureParams(PostingListParams &params);
    static void remove(const vespalib::string &prefix);
};

}
