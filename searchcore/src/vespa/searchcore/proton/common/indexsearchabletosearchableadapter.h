// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/indexsearchable.h>
#include <vespa/searchlib/queryeval/searchable.h>

namespace proton {

class IndexSearchableToSearchableAdapter : public search::queryeval::Searchable {
private:
    searchcorespi::IndexSearchable::SP          _searchable;
    const search::attribute::IAttributeContext &_attrCtx;

public:
    IndexSearchableToSearchableAdapter(const searchcorespi::IndexSearchable::SP &searchable,
                                       const search::attribute::IAttributeContext &attrCtx)
        : _searchable(searchable),
          _attrCtx(attrCtx)
    {
    }

    /**
     * Implements search::queryeval::Searchable.
     */
    virtual search::queryeval::Blueprint::UP createBlueprint(const search::queryeval::IRequestContext & requestContext,
                                                             const search::queryeval::FieldSpec &field,
                                                             const search::query::Node &term) {
        return _searchable->createBlueprint(requestContext, field, term, _attrCtx);
    }

    virtual search::queryeval::Blueprint::UP createBlueprint(const search::queryeval::IRequestContext & requestContext,
                                                             const search::queryeval::FieldSpecList &fields,
                                                             const search::query::Node &term) {
        return _searchable->createBlueprint(requestContext, fields, term, _attrCtx);
    }
};


} // namespace proton


