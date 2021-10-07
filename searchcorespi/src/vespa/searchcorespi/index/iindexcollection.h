// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "indexsearchable.h"

namespace search::queryeval { class ISourceSelector; }
namespace searchcorespi {

/**
 * Interface for a set of index searchables with source ids,
 * and a source selector for determining which index searchable to use for each document.
 */
class IIndexCollection {
protected:
    using ISourceSelector = search::queryeval::ISourceSelector;
public:
    typedef std::unique_ptr<IIndexCollection> UP;
    typedef std::shared_ptr<IIndexCollection> SP;

    virtual ~IIndexCollection() {}

    /**
     * Returns the source selector used to determine which index to use for each document.
     */
    virtual const ISourceSelector &getSourceSelector() const = 0;

    /**
     * Returns the number sources (index searchables) for this collection.
     */
    virtual size_t getSourceCount() const = 0;

    /**
     * Returns the index searchable for source i (i in the range [0, getSourceCount()>).
     */
    virtual IndexSearchable &getSearchable(uint32_t i) const = 0;

    /**
     * Returns the source id for source i (i in the range [0, getSourceCount()>).
     * The source id is used for this source in the source selector.
     */
    virtual uint32_t getSourceId(uint32_t i) const = 0;

    virtual vespalib::string toString() const;

};

}  // namespace searchcorespi

