// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchlib/util/searchable_stats.h>

namespace searchcorespi {

/**
 * Abstract class extended by components to expose content that can be
 * searched by a query term. A IndexSearchable component supports searching
 * in one or more named fields. The Blueprint created by a Searchable
 * is an intermediate query representation that is later used to
 * create the actual search iterators used to produce matches.
 *
 * The class is a specialized version of search::queryeval::Searchable
 * that let the components access a per query attribute context that expose
 * attribute vectors that can be utilized during query evaluation.
 **/
class IndexSearchable
{
protected:
    typedef search::queryeval::IRequestContext IRequestContext;
    typedef search::queryeval::FieldSpec FieldSpec;
    typedef search::queryeval::FieldSpecList FieldSpecList;
    typedef search::query::Node Node;
    typedef search::attribute::IAttributeContext IAttributeContext;
    typedef search::queryeval::Blueprint Blueprint;
public:
    typedef std::shared_ptr<IndexSearchable> SP;

    IndexSearchable() {}

    virtual ~IndexSearchable() {}

    /**
     * Create a blueprint searching a single field.
     *
     * @return blueprint
     * @param field the field to search
     * @param term the query tree term
     * @param attrCtx the per query attribute context
     **/
    virtual Blueprint::UP
    createBlueprint(const IRequestContext & requestContext,
                    const FieldSpec &field,
                    const Node &term,
                    const IAttributeContext &attrCtx) = 0;

    /**
     * Create a blueprint searching a set of fields. The default
     * implementation of this function will create blueprints for
     * individual fields and combine them with an OR blueprint.
     *
     * @return blueprint
     * @param fields the set of fields to search
     * @param term the query tree term
     * @param attrCtx the per query attribute context
     **/
    virtual Blueprint::UP
    createBlueprint(const IRequestContext & requestContext,
                    const FieldSpecList &fields,
                    const Node &term,
                    const IAttributeContext &attrCtx);

    /**
     * Returns the searchable stats for this index searchable.
     */
    virtual search::SearchableStats getSearchableStats() const = 0;
};

} // namespace searchcorespi
