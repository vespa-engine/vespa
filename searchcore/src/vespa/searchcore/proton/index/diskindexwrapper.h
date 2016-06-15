// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/idiskindex.h>
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/diskindex/diskindex.h>

namespace proton {

class DiskIndexWrapper : public searchcorespi::index::IDiskIndex {
private:
    search::diskindex::DiskIndex _index;

public:
    DiskIndexWrapper(const vespalib::string &indexDir,
                     const search::TuneFileSearch &tuneFileSearch,
                     size_t cacheSize);

    DiskIndexWrapper(const DiskIndexWrapper &oldIndex,
                     const search::TuneFileSearch &tuneFileSearch,
                     size_t cacheSize);

    /**
     * Implements searchcorespi::IndexSearchable
     */
    virtual search::queryeval::Blueprint::UP
    createBlueprint(const search::queryeval::IRequestContext & requestContext,
                    const search::queryeval::FieldSpec &field,
                    const search::query::Node &term,
                    const search::attribute::IAttributeContext &)
    {
        return _index.createBlueprint(requestContext, field, term);
    }
    virtual search::queryeval::Blueprint::UP
    createBlueprint(const search::queryeval::IRequestContext & requestContext,
                    const search::queryeval::FieldSpecList &fields,
                    const search::query::Node &term,
                    const search::attribute::IAttributeContext &)
    {
        return _index.createBlueprint(requestContext, fields, term);
    }
    virtual search::SearchableStats getSearchableStats() const {
        return search::SearchableStats()
            .sizeOnDisk(_index.getSize());
    }

    /**
     * Implements proton::IDiskIndex
     */
    virtual const vespalib::string &getIndexDir() const { return _index.getIndexDir(); }
    virtual const search::index::Schema &getSchema() const { return _index.getSchema(); }
};

}  // namespace proton

