// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/queryeval/searchiterator.h>

#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>

using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;

#include <map>
#include <vector>
#include <string>

namespace search::fakedata {

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
    using SP = std::shared_ptr<FakePosting>;

    FakePosting(const std::string &name);

    virtual ~FakePosting();

    /*
     * Size of posting list, in bits.
     */
    virtual size_t bitSize() const = 0;
    virtual size_t skipBitSize() const;
    virtual size_t l1SkipBitSize() const;
    virtual size_t l2SkipBitSize() const;
    virtual size_t l3SkipBitSize() const;
    virtual size_t l4SkipBitSize() const;
    virtual bool hasWordPositions() const = 0;
    virtual bool has_interleaved_features() const;
    virtual bool enable_unpack_normal_features() const;
    virtual bool enable_unpack_interleaved_features() const;

    /*
     * Single posting list performance, without feature unpack.
     */
    virtual int lowLevelSinglePostingScan() const = 0;

    /*
     * Single posting list performance, with feature unpack.
     */
    virtual int lowLevelSinglePostingScanUnpack() const = 0;

    /*
     * Two posting lists performance (same format) without feature unpack.
     */
    virtual int lowLevelAndPairPostingScan(const FakePosting &rhs) const = 0;

    /*
     * Two posting lists performance (same format) with feature unpack.
     */
    virtual int lowLevelAndPairPostingScanUnpack(const FakePosting &rhs) const = 0;


    /*
     * Iterator factory, for current query evaluation framework.
     */
    virtual std::unique_ptr<search::queryeval::SearchIterator>
    createIterator(const fef::TermFieldMatchDataArray &matchData) const = 0;

    const std::string &getName() const
    {
        return _name;
    }
};

}
