// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/searchable.h>

#include <memory>

namespace searchcorespi { class IndexSearchable; }

namespace proton::matching {

/**
 * Interface used to expose searchable data to the matching
 * pipeline. Ownership of the objects exposed through this interface
 * is handled by the implementation. Cleanup is triggered by deleting
 * the context interface. All searchable attributes are exposed
 * through a single instance of the Searchable interface. Indexed
 * fields are exposed as multiple Searchable instances that are
 * assigned separate source ids. A source selector is used to
 * determine which source should be used for each document.
 **/
class ISearchContext
{
protected:
    ISearchContext() = default;
public:
    /**
     * Convenience typedef for an auto pointer to this interface.
     **/
    typedef std::unique_ptr<ISearchContext> UP;
    ISearchContext(const ISearchContext &) = delete;
    ISearchContext & operator = (const ISearchContext &) = delete;

    typedef search::queryeval::Searchable      Searchable;
    using IndexSearchable = searchcorespi::IndexSearchable;

    /**
     * Obtain the index fields searchable.
     *
     * @return index fields searchable.
     **/
    virtual IndexSearchable &getIndexes() = 0;

    /**
     * Obtain the attribute fields searchable.
     *
     * @return attribute fields searchable.
     **/
    virtual Searchable &getAttributes() = 0;

    /**
     * Obtain the limit value for local document ids. This value is
     * larger than all local docids that are currently in use. It will
     * be used both to terminate matching and as an estimate on the
     * total number of documents.
     *
     * @return local document id limit value
     **/
    virtual uint32_t getDocIdLimit() = 0;

    /**
     * Deleting the context will trigger cleanup in the
     * implementation.
     **/
    virtual ~ISearchContext() = default;
};

}
