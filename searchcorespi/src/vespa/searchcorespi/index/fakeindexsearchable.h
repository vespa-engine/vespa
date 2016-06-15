// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "indexsearchable.h"
#include <vespa/searchlib/queryeval/fake_searchable.h>

namespace searchcorespi {

/**
 * A fake index searchable used for unit testing.
 */
class FakeIndexSearchable : public IndexSearchable {
private:
    search::queryeval::FakeSearchable _fake;

public:
    FakeIndexSearchable()
        : _fake()
    {
    }

    search::queryeval::FakeSearchable &getFake() { return _fake; }
    
    /**
     * Implements IndexSearchable
     */
    virtual Blueprint::UP
    createBlueprint(const IRequestContext & requestContext,
                    const FieldSpec &field,
                    const Node &term,
                    const IAttributeContext &)
    {
        return _fake.createBlueprint(requestContext, field, term);
    }

    virtual search::SearchableStats getSearchableStats() const {
        return search::SearchableStats();
    }
};

}  // namespace searchcorespi

