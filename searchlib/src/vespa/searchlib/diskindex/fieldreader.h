// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/index/postinglistcounts.h>
#include <vespa/searchlib/index/dictionaryfile.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/index/postinglistfile.h>
#include <vespa/searchlib/index/schemautil.h>
#include <vespa/searchlib/util/postingpriorityqueue.h>
#include "wordnummapper.h"
#include "docidmapper.h"
#include "fieldwriter.h"

namespace search
{

namespace diskindex
{

class FieldReaderFieldInfo;

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
    FieldReader(const FieldReader &rhs) = delete;
    FieldReader(const FieldReader &&rhs) = delete;
    FieldReader &operator=(const FieldReader &rhs) = delete;
    FieldReader &operator=(const FieldReader &&rhs) = delete;
public:
    using DictionaryFileSeqRead = index::DictionaryFileSeqRead;

    typedef index::PostingListFileSeqRead PostingListFileSeqRead;
    typedef index::DocIdAndFeatures DocIdAndFeatures;
    typedef index::Schema Schema;
    typedef index::SchemaUtil::IndexIterator IndexIterator;
    typedef index::PostingListCounts PostingListCounts;
    typedef index::PostingListParams PostingListParams;

    uint64_t _wordNum;
    DocIdAndFeatures _docIdAndFeatures;
protected:
    std::unique_ptr<DictionaryFileSeqRead> _dictFile;
    std::unique_ptr<PostingListFileSeqRead> _oldposoccfile;
    WordNumMapper _wordNumMapper;
    DocIdMapper _docIdMapper;
    uint64_t _oldWordNum;
    uint32_t _residue;
    uint32_t _docIdLimit;
    bool _checkPointResume;
    vespalib::string _word;

    static uint64_t
    noWordNumHigh()
    {
        return std::numeric_limits<uint64_t>::max();
    }

    static uint64_t
    noWordNum()
    {
        return 0u;
    }

    void
    readCounts();

    void
    readDocIdAndFeatures();

public:
    FieldReader();

    virtual
    ~FieldReader();

    virtual void
    read();

    virtual bool
    allowRawFeatures();

    void
    write(FieldWriter &writer)
    {
        if (_wordNum != writer.getSparseWordNum()) {
            writer.newWord(_wordNum, _word);
        }
        writer.add(_docIdAndFeatures);
    }

    bool
    isValid() const
    {
        return _wordNum != noWordNumHigh();
    }

    bool
    operator<(const FieldReader &rhs) const
    {
        return _wordNum < rhs._wordNum ||
            (_wordNum == rhs._wordNum &&
             _docIdAndFeatures._docId < rhs._docIdAndFeatures._docId);
    }

    virtual void
    setup(const WordNumMapping &wordNumMapping,
          const DocIdMapping &docIdMapping);

    virtual bool
    earlyOpen(const vespalib::string &prefix,
              const TuneFileSeqRead &tuneFileRead);

    virtual bool
    lateOpen(const vespalib::string &prefix,
             const TuneFileSeqRead &tuneFileRead);

    virtual bool
    open(const vespalib::string &prefix, const TuneFileSeqRead &tuneFileRead);

    virtual bool
    close();

    /*
     * To be called between words, not in the middle of one.
     */
    virtual void
    checkPointWrite(vespalib::nbostream &out);

    /*
     * To be called after earlyOpen() but before afterOpen().
     */
    virtual void
    checkPointRead(vespalib::nbostream &in);

    virtual void
    setFeatureParams(const PostingListParams &params);

    virtual void
    getFeatureParams(PostingListParams &params);

    uint32_t
    getDocIdLimit() const
    {
        return _docIdLimit;
    }

    static std::unique_ptr<FieldReader>
    allocFieldReader(const IndexIterator &index, const Schema &oldSchema);
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

    virtual bool
    earlyOpen(const vespalib::string &prefix,
              const TuneFileSeqRead &tuneFileRead) override;

    virtual bool
    lateOpen(const vespalib::string &prefix,
             const TuneFileSeqRead &tuneFileRead) override;

    virtual bool
    open(const vespalib::string &prefix, const TuneFileSeqRead &tuneFileRead)
        override;

    virtual void
    getFeatureParams(PostingListParams &params) override;
};

/*
 * Field reader that strips information from source, e.g. remove
 * weights or discard nonzero elements, due to collection type change.
 */
class FieldReaderStripInfo : public FieldReader
{
private:
    bool _hasElements;
    bool _hasElementWeights;
public:
    FieldReaderStripInfo(const IndexIterator &index);

    virtual bool
    allowRawFeatures() override;

    virtual void
    read() override;

    virtual void
    getFeatureParams(PostingListParams &params) override;
};


} // namespace diskindex

} // namespace search

