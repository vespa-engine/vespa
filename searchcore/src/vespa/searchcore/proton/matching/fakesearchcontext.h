// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchcontext.h"
#include <vespa/searchcorespi/index/fakeindexsearchable.h>
#include <vespa/searchcorespi/index/indexcollection.h>
#include <vespa/searchlib/attribute/fixedsourceselector.h>
#include <algorithm>
#include <map>
#include <vector>

namespace proton::matching {

using searchcorespi::FakeIndexSearchable;
using searchcorespi::IndexSearchable;
using searchcorespi::IndexCollection;

class FakeSearchContext : public ISearchContext
{
public:
    typedef search::queryeval::FakeSearchable FakeSearchable;

private:
    vespalib::Clock                        _clock;
    vespalib::Doom                         _doom;
    search::queryeval::ISourceSelector::SP _selector;
    IndexCollection::SP                    _indexes;
    FakeSearchable                         _attrSearchable;
    uint32_t                               _docIdLimit;

public:
    FakeSearchContext(size_t initialNumDocs=0);
    ~FakeSearchContext();

    FakeSearchContext &addIdx(uint32_t id) {
        _indexes->append(id, IndexSearchable::SP(new FakeIndexSearchable()));
        return *this;
    }

    FakeSearchContext &setLimit(uint32_t limit) {
        _docIdLimit = limit;
        return *this;
    }

    FakeSearchable &attr() { return _attrSearchable; }

    FakeIndexSearchable &idx(uint32_t i) {
        return static_cast<FakeIndexSearchable &>(_indexes->getSearchable(i));
    }

    search::queryeval::ISourceSelector &selector() { return *_selector; }

    // Implements ISearchContext
    search::queryeval::Searchable &getIndexes() override {
        return *_indexes;
    }

    search::queryeval::Searchable &getAttributes() override {
        return _attrSearchable;
    }

    uint32_t getDocIdLimit() override {
        return _docIdLimit;
    }
    virtual const vespalib::Doom & getDoom() const { return _doom; }
};

}
