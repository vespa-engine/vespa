// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "postinglistparams.h"
#include "postinglistcounts.h"
#include <vespa/searchlib/common/tunefileinfo.h>

namespace vespalib { class nbostream; }

namespace search {

namespace common { class FileHeaderContext; }

namespace index {

class PostingListCounts;
class PostingListHandle;

/**
 * Interface for count files describing where in a posting list file
 * the various words are located.  It is merged at index time with a
 * text-only dictionary to produce a binary dictionary optimized for
 * random access used at search time.
 *
 * TODO: Might want to allow semi-random access for prefix searches,
 * allowing for less data in posting list files being duplicated from
 * the count file.
 */
class PostingListCountFileSeqRead
{
public:
    PostingListCountFileSeqRead();

    virtual ~PostingListCountFileSeqRead();

    /**
     * Checkpoint write.  Used at semi-regular intervals during indexing
     * to allow for continued indexing after an interrupt.  Implies
     * flush from memory to disk, and possibly also sync to permanent
     * storage media.
     */
    virtual void checkPointWrite(vespalib::nbostream &out) = 0;

    /**
     * Checkpoint read.  Used when resuming indexing after an interrupt.
     */
    virtual void checkPointRead(vespalib::nbostream &in) = 0;

    /**
     * Open posting list count file for sequential read.
     */
    virtual bool open(const vespalib::string &name, const TuneFileSeqRead &tuneFileRead) = 0;

    /**
     * Close posting list count file.
     */
    virtual bool close() = 0;

    /*
     * Get current parameters.
     */
    virtual void getParams(PostingListParams &params);
};


class PostingListCountFileSeqWrite
{
public:
    PostingListCountFileSeqWrite();

    virtual ~PostingListCountFileSeqWrite();

    /**
     * Checkpoint write.  Used at semi-regular intervals during indexing
     * to allow for continued indexing after an interrupt.  Implies
     * flush from memory to disk, and possibly also sync to permanent
     * storage media.
     */
    virtual void checkPointWrite(vespalib::nbostream &out) = 0;

    /**
     * Checkpoint read.  Used when resuming indexing after an interrupt.
     */
    virtual void checkPointRead(vespalib::nbostream &in) = 0;

    /**
     * Open posting list count file for sequential write.
     */
    virtual bool open(const vespalib::string &name,
                      const TuneFileSeqWrite &tuneFileWrite,
                      const common::FileHeaderContext &fileHeaderContext) = 0;

    /**
     * Close posting list count file.
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
};


} // namespace index

} // namespace search

