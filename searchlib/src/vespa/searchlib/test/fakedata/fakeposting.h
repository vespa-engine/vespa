// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>

#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;

#include <map>
#include <vector>
#include <string>

namespace search
{

namespace fakedata
{

/*
 * Base class for faked posting list formats.
 */
class FakePosting
{
private:
    FakePosting(const FakePosting &);

    FakePosting &
    operator=(const FakePosting &);

    std::string _name;
public:
    typedef std::shared_ptr<FakePosting> SP;

    FakePosting(const std::string &name);

    virtual ~FakePosting(void);

    /*
     * Size of posting list, in bits.
     */
    virtual size_t
    bitSize(void) const = 0;

    virtual size_t
    skipBitSize(void) const;

    virtual size_t
    l1SkipBitSize(void) const;

    virtual size_t
    l2SkipBitSize(void) const;

    virtual size_t
    l3SkipBitSize(void) const;

    virtual size_t
    l4SkipBitSize(void) const;

    virtual bool
    hasWordPositions(void) const = 0;

    /*
     * Single posting list performance, without feature unpack.
     */
    virtual int
    lowLevelSinglePostingScan(void) const = 0;

    /*
     * Single posting list performance, with feature unpack.
     */
    virtual int
    lowLevelSinglePostingScanUnpack(void) const = 0;

    /*
     * Two posting lists performance (same format) without feature unpack.
     */
    virtual int
    lowLevelAndPairPostingScan(const FakePosting &rhs) const = 0;

    /*
     * Two posting lists performance (same format) with feature unpack.
     */
    virtual int
    lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const = 0;


    /*
     * Iterator factory, for current query evaluation framework.
     */
    virtual search::queryeval::SearchIterator *
    createIterator(const fef::TermFieldMatchDataArray &matchData) const = 0;

    const std::string &getName(void) const
    {
        return _name;
    }
};

} // namespace fakedata

} // namespace search

