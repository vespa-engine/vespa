// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "postinglistcounts.h"
#include "postinglisthandle.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <map>
#include <vector>
#include <string>
#include <limits>

namespace search
{

namespace common
{

class FileHeaderContext;

}

namespace index
{

class OldDictionaryIndexMapping
{
private:
    std::vector<uint32_t> _fieldIdToLocalId;
    std::vector<vespalib::string> _indexNames;
    std::vector<uint32_t> _indexIds;
    std::vector<uint32_t> _washedIndexIds;

    void
    setupHelper(const Schema &schema);

public:
    OldDictionaryIndexMapping();

    ~OldDictionaryIndexMapping();

    static uint32_t
    noLocalId()
    {
        return std::numeric_limits<uint32_t>::max();
    }

    uint32_t
    getLocalId(uint32_t dfid) const
    {
        if (dfid < _fieldIdToLocalId.size())
            return _fieldIdToLocalId[dfid];
        else
            return noLocalId();
    }

    uint32_t
    getExternalId(uint32_t localId) const
    {
        return _indexIds[localId];
    }

    void
    setup(const Schema &schema,
          const std::vector<vespalib::string> &indexNames);

    void
    setup(const Schema &schema,
          const std::vector<uint32_t> &indexes);

    const std::vector<uint32_t> &
    getIndexIds() const
    {
        return _indexIds;
    }

    const std::vector<uint32_t> &
    getWashedIndexIds() const
    {
        return _washedIndexIds;
    }

    const std::vector<vespalib::string> &
    getIndexNames() const
    {
        return _indexNames;
    }

    uint32_t
    getNumIndexes() const
    {
        return _indexIds.size();
    }
};


/**
 * Interface for dictionary file containing words and counts for words.
 *
 * This is "at" schema level.
 */
class OldDictionaryFileSeqRead
{
public:
    OldDictionaryFileSeqRead()
    {
    }

    virtual
    ~OldDictionaryFileSeqRead();

    /**
     * Read word and counts.  Only nonzero counts are returned. If at
     * end of dictionary then noWordNumHigh() is returned as word number.
     */
    virtual void
    readWord(vespalib::string &word,
             uint64_t &wordNum,
             std::vector<uint32_t> &indexes,
             std::vector<PostingListCounts> &counts) = 0;

    /**
     * Open dictionary file for sequential read.  The supplied schema
     * decides what existing indexes are visible (i.e. indexes in dictionary
     * but not in schema are hidden).  A dictionary might have no visible
     * indexes.
     */
    virtual bool
    open(const vespalib::string &name, const Schema &schema,
         const TuneFileSeqRead &tuneFileRead) = 0;

    /**
     * Close dictionary file.
     */
    virtual bool
    close() = 0;

    /*
     * Get visible indexes available in dictionary.
     */
    virtual void
    getIndexes(std::vector<uint32_t> &indexes) = 0;

    static uint64_t
    noWordNum()
    {
        return 0u;
    }

    static uint64_t
    noWordNumHigh()
    {
        return std::numeric_limits<uint64_t>::max();
    }
};

/**
 * Interface for dictionary file containing words and count for words.
 *
 * This is "at" schema level.
 *
 * The file should contain the set of field names for which the dictionary
 * is valid, to simplify handling of schema changes.
 */
class OldDictionaryFileSeqWrite
{
protected:
public:
    OldDictionaryFileSeqWrite()
    {
    }

    virtual
    ~OldDictionaryFileSeqWrite();

    /**
     * Write word and counts.  Only nonzero counts should be supplied.
     */
    virtual void
    writeWord(vespalib::stringref word,
              const std::vector<uint32_t> &indexes,
              const std::vector<PostingListCounts> &counts) = 0;

    /**
     * Open dictionary file for sequential write.  The field with most
     * words should be first for optimal compression.
     */
    virtual bool
    open(const vespalib::string &name,
         uint32_t numWords,
         uint32_t chunkSize,
         const std::vector<uint32_t> &indexes,
         const Schema &schema,
         const TuneFileSeqWrite &tuneFileWrite,
         const common::FileHeaderContext &fileHeaderContext) = 0;

    /**
     * Close dictionary file.
     */
    virtual bool
    close() = 0;
};


} // namespace index

} // namespace search

