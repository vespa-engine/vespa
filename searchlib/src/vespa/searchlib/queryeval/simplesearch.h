// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchiterator.h"
#include "simpleresult.h"

namespace search::queryeval {

/**
 * Simple search class used to return a predefined set of
 * results. This class will mostly be used for testing.
 **/
class SimpleSearch : public SearchIterator
{
private:
    vespalib::string _tag;
    SimpleResult     _result;
    uint32_t         _index;
    bool             _strict;

    SimpleSearch(const SimpleSearch &);
    SimpleSearch &operator=(const SimpleSearch &);

protected:
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;

public:
    SimpleSearch(const SimpleResult &result, bool strict = true);
    SimpleSearch &tag(const vespalib::string &t) {
        _tag = t;
        return *this;
    }
    Trinary is_strict() const override { return _strict ? Trinary::True : Trinary::False; }
    void initRange(uint32_t begin_id, uint32_t end_id) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    ~SimpleSearch();
};

}
