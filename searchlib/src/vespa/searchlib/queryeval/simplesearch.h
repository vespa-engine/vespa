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
    vespalib::string  _tag;
    SimpleResult _result;
    uint32_t     _index;

    SimpleSearch(const SimpleSearch &);
    SimpleSearch &operator=(const SimpleSearch &);

protected:
    void doSeek(uint32_t docid) override;
    void doUnpack(uint32_t docid) override;

public:
    SimpleSearch(const SimpleResult &result);
    SimpleSearch &tag(const vespalib::string &t) {
        _tag = t;
        return *this;
    }
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    ~SimpleSearch();
};

}
