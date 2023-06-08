// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "wordnummapper.h"
#include "docidmapper.h"
#include "fieldwriter.h"
#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/index/schemautil.h>

namespace search::diskindex {

class FieldLengthScanner;

/*
 * FieldReader is used to read a dictionary and posting list file
 * together, and get a sequential view of the stored data.
 *
 * It can use mappings for word numbers and document ids to skip
 * documents that are logically removed and use shared word numbers
 * with other field readers.
 *
 * It is used by the fusion code as one of many input objects connected
 * to a FieldWriter class that writes the merged output for the field.
 */
class FieldReader
{
private:
    void VESPA_DLL_LOCAL readCounts();
    void VESPA_DLL_LOCAL readDocIdAndFeatures();
public:
    using DictionaryFileSeqRead = index::DictionaryFileSeqRead;

    using PostingListFileSeqRead = index::PostingListFileSeqRead;
    using DocIdAndFeatures = index::DocIdAndFeatures;
    using Schema = index::Schema;
    using IndexIterator = index::SchemaUtil::IndexIterator;
    using PostingListCounts = index::PostingListCounts;
    using PostingListParams = index::PostingListParams;

    uint64_t         _wordNum;
    DocIdAndFeatures _docIdAndFeatures;
protected:
    std::unique_ptr<DictionaryFileSeqRead> _dictFile;
    std::unique_ptr<PostingListFileSeqRead> _oldposoccfile;
    WordNumMapper _wordNumMapper;
    DocIdMapper _docIdMapper;
    uint64_t _oldWordNum;
    uint32_t _residue;
    uint32_t _docIdLimit;
    vespalib::string _word;

    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }

    static uint64_t noWordNum() {
        return 0u;
    }

public:
    FieldReader(const FieldReader &rhs) = delete;
    FieldReader(const FieldReader &&rhs) = delete;
    FieldReader &operator=(const FieldReader &rhs) = delete;
    FieldReader &operator=(const FieldReader &&rhs) = delete;

    FieldReader();

    virtual ~FieldReader();

    virtual void read();
    virtual bool allowRawFeatures();
    virtual bool need_regenerate_interleaved_features_scan();
    virtual void scan_element_lengths(uint32_t scan_chunk);

    void write(FieldWriter &writer) {
        if (_wordNum != writer.getSparseWordNum()) {
            writer.newWord(_wordNum, _word);
        }
        writer.add(_docIdAndFeatures);
    }

    bool isValid() const { return _wordNum != noWordNumHigh(); }

    bool operator<(const FieldReader &rhs) const {
        return _wordNum < rhs._wordNum ||
            (_wordNum == rhs._wordNum &&
             _docIdAndFeatures.doc_id() < rhs._docIdAndFeatures.doc_id());
    }

    virtual void setup(const WordNumMapping &wordNumMapping, const DocIdMapping &docIdMapping);
    virtual bool open(const vespalib::string &prefix, const TuneFileSeqRead &tuneFileRead);
    virtual bool close();
    virtual void setFeatureParams(const PostingListParams &params);
    virtual void getFeatureParams(PostingListParams &params);
    uint32_t getDocIdLimit() const { return _docIdLimit; }
    const index::FieldLengthInfo &get_field_length_info() const;

    static std::unique_ptr<FieldReader> allocFieldReader(const IndexIterator &index, const Schema &oldSchema, std::shared_ptr<FieldLengthScanner> field_length_scanner);
};


/*
 * Field reader that pretends that input is empty, e.g. due to field
 * not existing in source or being incompatible.
 */
class FieldReaderEmpty : public FieldReader
{
private:
    const IndexIterator _index;

public:
    FieldReaderEmpty(const IndexIterator &index);
    bool open(const vespalib::string &prefix, const TuneFileSeqRead &tuneFileRead) override;
    void getFeatureParams(PostingListParams &params) override;
};

/*
 * Field reader that strips information from source, e.g. remove
 * weights or discard nonzero elements, due to collection type change.
 * It is also used to regenerate interleaved features from normal features.
 */
class FieldReaderStripInfo : public FieldReader
{
private:
    bool _hasElements;
    bool _hasElementWeights;
    bool _want_interleaved_features;
    bool _regenerate_interleaved_features;
    std::shared_ptr<FieldLengthScanner> _field_length_scanner;
public:
    FieldReaderStripInfo(const IndexIterator &index, std::shared_ptr<FieldLengthScanner>);
    bool allowRawFeatures() override;
    bool need_regenerate_interleaved_features_scan() override;
    bool open(const vespalib::string &prefix, const TuneFileSeqRead &tuneFileRead) override;
    void read() override;
    void scan_element_lengths(uint32_t scan_chunk) override;
    void getFeatureParams(PostingListParams &params) override;
};

}
