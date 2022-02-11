// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "postinglisthandle.h"
#include "postinglistcountfile.h"
#include <vespa/searchlib/common/tunefileinfo.h>
#include <limits>

class FastOS_FileInterface;

namespace search::common { class FileHeaderContext; }

namespace search::index {

/**
 * Interface for dictionary file containing words and counts for words.
 */
class DictionaryFileSeqRead : public PostingListCountFileSeqRead {
public:
    DictionaryFileSeqRead() { }
    ~DictionaryFileSeqRead();

    /**
     * Read word and counts.  Only nonzero counts are returned. If at
     * end of dictionary then noWordNumHigh() is returned as word number.
     */
    virtual void readWord(vespalib::string &word, uint64_t &wordNum, PostingListCounts &counts) = 0;

    static uint64_t noWordNum() { return 0u; }

    static uint64_t noWordNumHigh() {
        return std::numeric_limits<uint64_t>::max();
    }
};

/**
 * Interface for dictionary file containing words and count for words.
 */
class DictionaryFileSeqWrite : public PostingListCountFileSeqWrite {
public:
    DictionaryFileSeqWrite() { }
    ~DictionaryFileSeqWrite();

    /**
     * Write word and counts.  Only nonzero counts should be supplied.
     */
    virtual void writeWord(vespalib::stringref word, const PostingListCounts &counts) = 0;
};


/**
 * Interface for dictionary file containing words and counts.
 */
class DictionaryFileRandRead {
protected:
    // Can be examined after open
    bool _memoryMapped;
public:
    DictionaryFileRandRead();
    virtual ~DictionaryFileRandRead();

    virtual bool lookup(vespalib::stringref word, uint64_t &wordNum,
                        PostingListOffsetAndCounts &offsetAndCounts) = 0;

    /**
     * Open dictionary file for random read.
     */
    virtual bool open(const vespalib::string &name, const TuneFileRandRead &tuneFileRead) = 0;

    /**
     * Close dictionary file.
     */
    virtual bool close() = 0;

    bool getMemoryMapped() const { return _memoryMapped; }

    virtual uint64_t getNumWordIds() const = 0;
protected:
    void afterOpen(FastOS_FileInterface &file);
};

}
