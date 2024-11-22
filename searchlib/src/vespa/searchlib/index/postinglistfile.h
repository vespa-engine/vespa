// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "dictionary_lookup_result.h"
#include "postinglistcounts.h"
#include "postinglisthandle.h"
#include <vespa/searchlib/common/tunefileinfo.h>
#include <string>

class FastOS_FileInterface;

namespace search::common { class FileHeaderContext; }
namespace search::fef { class TermFieldMatchDataArray; }
namespace search::queryeval { class SearchIterator; }

namespace search::index {

class DocIdAndFeatures;
class FieldLengthInfo;
class PostingListParams;

/**
 * Interface for posting list files containing document ids and features
 * for words.
 */
class PostingListFileSeqRead {
public:
    PostingListFileSeqRead();

    virtual ~PostingListFileSeqRead();

    /**
     * Read document id and features.
     */
    virtual void readDocIdAndFeatures(DocIdAndFeatures &features) = 0;

    /**
     * Read counts for a word.
     */
    virtual void read_word_and_counts(const std::string& word, const PostingListCounts& counts) = 0;

    /**
     * Open posting list file for sequential read.
     */
    virtual bool open(const std::string &name, const TuneFileSeqRead &tuneFileRead) = 0;

    /**
     * Close posting list file.
     */
    virtual bool close() = 0;

    /*
     * Get current parameters.
     */
    virtual void getParams(PostingListParams &params);

    /*
     * Set (word, docid) feature parameters.
     *
     * Typically can only enable or disable cooked features.
     */
    virtual void setFeatureParams(const PostingListParams &params);

    /*
     * Get current (word, docid) feature parameters.
     */
    virtual void getFeatureParams(PostingListParams &params);

    virtual const FieldLengthInfo &get_field_length_info() const = 0;
};

/**
 * Interface for posting list files containing document ids and features
 * for words.
 */
class PostingListFileSeqWrite {
protected:
    PostingListCounts _counts;
public:
    PostingListFileSeqWrite();
    virtual ~PostingListFileSeqWrite();

    /**
     * Write document id and features.
     */
    virtual void writeDocIdAndFeatures(const DocIdAndFeatures &features) = 0;

    /**
     * Flush word (during write) after it is complete to buffers, i.e.
     * prepare for next word, but not for application crash.
     */
    virtual void flushWord() = 0;

    /**
     * Open posting list file for sequential write.
     */
    virtual bool
    open(const std::string &name,
         const TuneFileSeqWrite &tuneFileWrite,
         const common::FileHeaderContext &fileHeaderContext) = 0;

    /**
     * Close posting list file.
     */
    virtual bool close() = 0;

    /*
     * Set parameters.
     */
    virtual void setParams(const PostingListParams &params);

    /*
     * Get current parameters.
     */
    virtual void getParams(PostingListParams &params);

    /*
     * Set (word, docid) feature parameters.
     */
    virtual void setFeatureParams(const PostingListParams &params);

    /*
     * Get current (word, docid) feature parameters.
     */
    virtual void getFeatureParams(PostingListParams &params);

    PostingListCounts &getCounts() { return _counts; }
};


/**
 * Interface for posting list files containing document ids and features
 * for words.
 */
class PostingListFileRandRead {
protected:
    // Can be examined after open
    bool _memoryMapped;
public:
    using SP = std::shared_ptr<PostingListFileRandRead>;

    PostingListFileRandRead();
    virtual ~PostingListFileRandRead();

    /**
     * Create iterator for single word.  Semantic lifetime of counts and
     * handle must exceed lifetime of iterator.
     *
     * XXX: TODO: How to read next set of segments from disk if handle
     * didn't cover the whole word, probably need access to higher level
     * API above caches.
     */
    virtual std::unique_ptr<search::queryeval::SearchIterator>
    createIterator(const DictionaryLookupResult& lookup_result,
                   const PostingListHandle& handle,
                   const search::fef::TermFieldMatchDataArray &matchData) const = 0;


    /**
     * Read posting list into handle.
     */
    virtual PostingListHandle read_posting_list(const DictionaryLookupResult& lookup_result) = 0;

    /**
     * Remove directio padding from posting list.
     */
    virtual void trim_posting_list(const DictionaryLookupResult& lookup_result, PostingListHandle& handle) const = 0;

    /**
     * Open posting list file for random read.
     */
    virtual bool open(const std::string &name, const TuneFileRandRead &tuneFileRead) = 0;

    /**
     * Close posting list file.
     */
    virtual bool close() = 0;

    virtual const FieldLengthInfo &get_field_length_info() const = 0;

    bool getMemoryMapped() const { return _memoryMapped; }

protected:
    void afterOpen(FastOS_FileInterface &file);
};


/**
 * Passthrough class.
 */
class PostingListFileRandReadPassThrough : public PostingListFileRandRead {
protected:
    PostingListFileRandRead *_lower;
    bool _ownLower;

public:
    PostingListFileRandReadPassThrough(PostingListFileRandRead *lower, bool ownLower);
    ~PostingListFileRandReadPassThrough();

    std::unique_ptr<search::queryeval::SearchIterator>
    createIterator(const DictionaryLookupResult& lookup_result,
                   const PostingListHandle& handle,
                   const search::fef::TermFieldMatchDataArray &matchData) const override;

    PostingListHandle read_posting_list(const DictionaryLookupResult& lookup_result) override;
    void trim_posting_list(const DictionaryLookupResult& lookup_result, PostingListHandle& handle) const override;

    bool open(const std::string &name, const TuneFileRandRead &tuneFileRead) override;
    bool close() override;
};

}
