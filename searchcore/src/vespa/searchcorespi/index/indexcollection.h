// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isearchableindexcollection.h"
#include <vespa/searchlib/util/searchable_stats.h>

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

        SourceWithId(uint32_t id_in, const IndexSearchable::SP &source_in)
            : id(id_in), source_wrapper(source_in)
        {}
        SourceWithId() : id(0), source_wrapper() {}
    };

    // Selector shared across memory dumps, replaced on disk fusion operations
    using ISourceSelectorSP = std::shared_ptr<ISourceSelector>;
    ISourceSelectorSP         _source_selector;
    std::vector<SourceWithId> _sources;

public:
    IndexCollection(const ISourceSelectorSP & selector);
    IndexCollection(const ISourceSelectorSP & selector, const ISearchableIndexCollection &sources);
    ~IndexCollection();

    void append(uint32_t id, const IndexSearchable::SP &source) override;
    void replace(uint32_t id, const IndexSearchable::SP &source) override;
    IndexSearchable::SP getSearchableSP(uint32_t i) const override;
    void setSource(uint32_t docId)  override;


    // Implements IIndexCollection
    const ISourceSelector &getSourceSelector() const override;
    size_t getSourceCount() const override;
    IndexSearchable &getSearchable(uint32_t i) const override;
    uint32_t getSourceId(uint32_t i) const override;

    // Implements IndexSearchable
    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const IRequestContext & requestContext, const FieldSpec &field, const Node &term) override;
    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const IRequestContext & requestContext, const FieldSpecList &fields, const Node &term) override;
    search::SearchableStats getSearchableStats() const  override;
    search::SerialNum getSerialNum() const override;
    void accept(IndexSearchableVisitor &visitor) const override;

    static ISearchableIndexCollection::UP
    replaceAndRenumber(const ISourceSelectorSP & selector, const ISearchableIndexCollection &fsc,
                       uint32_t id_diff, const IndexSearchable::SP &new_source);

    // Implements IFieldLengthInspector
    /**
     * Returns field length info from the newest disk index, or empty info for all fields if no disk index exists.
     */
    search::index::FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override;
};

}  // namespace searchcorespi

