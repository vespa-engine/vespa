// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iindexcollection.h"
#include "indexsearchable.h"

namespace searchcorespi {

/**
 * Interface to both an IndexCollection and to an IndexSearchable
 */
class ISearchableIndexCollection : public IIndexCollection,
                                   public IndexSearchable {
public:
    ISearchableIndexCollection() : _currentIndex(-1) { }
    using UP = std::unique_ptr<ISearchableIndexCollection>;
    using SP = std::shared_ptr<ISearchableIndexCollection>;

    virtual void append(uint32_t id, const IndexSearchable::SP &source) = 0;
    virtual void replace(uint32_t id, const IndexSearchable::SP &source) = 0;
    virtual IndexSearchable::SP getSearchableSP(uint32_t i) const = 0;
    virtual void setSource(uint32_t docId) = 0;

    void setCurrentIndex(uint32_t id);
    uint32_t getCurrentIndex() const;
    bool valid() const;

private:
    int32_t _currentIndex;
};

}  // namespace searchcorespi

