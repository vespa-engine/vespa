// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/index/i_field_length_inspector.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/queryeval/field_spec.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <vespa/searchlib/queryeval/searchable.h>
#include <vespa/searchlib/util/searchable_stats.h>

namespace searchcorespi {

class IndexSearchableVisitor;

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
class IndexSearchable : public search::queryeval::Searchable,
                        public search::index::IFieldLengthInspector {
protected:
    using IRequestContext = search::queryeval::IRequestContext;
    using FieldSpec = search::queryeval::FieldSpec;
    using FieldSpecList = search::queryeval::FieldSpecList;
    using Node = search::query::Node;
    using IAttributeContext = search::attribute::IAttributeContext;
public:
    using SP = std::shared_ptr<IndexSearchable>;

    /**
     * Returns the searchable stats for this index searchable.
     */
    virtual search::SearchableStats getSearchableStats() const = 0;

    /**
     * Returns the serial number for this index searchable.
     */
    virtual search::SerialNum getSerialNum() const = 0;

    /**
     * Calls visitor with properly downcasted argument to differentiate
     * between different types of indexes (disk index or memory index).
     */
    virtual void accept(IndexSearchableVisitor &visitor) const = 0;
};

} // namespace searchcorespi
