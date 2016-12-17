// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchableindexcollection.h"
#include <vespa/searchlib/util/searchable_stats.h>
#include <memory>
#include <set>
#include <utility>
#include <vector>

namespace searchcorespi {

/**
 * Holds a set of index searchables with source ids, and a source selector for
 * determining which index to use for each document.
 */
class IndexCollection : public ISearchableIndexCollection
{
    struct SourceWithId {
        uint32_t id;
        IndexSearchable::SP source_wrapper;

        SourceWithId(uint32_t id_in,
                     const IndexSearchable::SP &source_in)
            : id(id_in), source_wrapper(source_in) {}
        SourceWithId() : id(0), source_wrapper() {}
    };

    // Selector shared across memory dumps, replaced on disk fusion operations
    using ISourceSelectorSP = std::shared_ptr<ISourceSelector>;
    ISourceSelectorSP         _source_selector;
    std::vector<SourceWithId> _sources;

public:
    IndexCollection(const ISourceSelectorSP & selector);
    IndexCollection(const ISourceSelectorSP & selector,
                    const ISearchableIndexCollection &sources);

    virtual void append(uint32_t id, const IndexSearchable::SP &source);
    virtual void replace(uint32_t id, const IndexSearchable::SP &source);
    virtual IndexSearchable::SP getSearchableSP(uint32_t i) const;
    virtual void setSource(uint32_t docId);


    // Implements IIndexCollection
    virtual const ISourceSelector &getSourceSelector() const;
    virtual size_t getSourceCount() const;
    virtual IndexSearchable &getSearchable(uint32_t i) const;
    virtual uint32_t getSourceId(uint32_t i) const;

    // Implements IndexSearchable
    virtual Blueprint::UP
    createBlueprint(const IRequestContext & requestContext,
                    const FieldSpec &field,
                    const Node &term,
                    const IAttributeContext &attrCtx);
    virtual Blueprint::UP
    createBlueprint(const IRequestContext & requestContext,
                    const FieldSpecList &fields,
                    const Node &term,
                    const IAttributeContext &attrCtx);
    virtual search::SearchableStats getSearchableStats() const;
    virtual search::SerialNum getSerialNum() const override;
    virtual void accept(IndexSearchableVisitor &visitor) const override;

    static ISearchableIndexCollection::UP replaceAndRenumber(
            const ISourceSelectorSP & selector,
            const ISearchableIndexCollection &fsc,
            uint32_t id_diff,
            const IndexSearchable::SP &new_source);
};

}  // namespace searchcorespi

