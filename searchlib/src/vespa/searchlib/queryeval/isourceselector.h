// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdint.h>
#include <vespa/searchlib/attribute/singlenumericattribute.h>

namespace search {
namespace queryeval {

typedef uint8_t Source;

/**
 * Component used to select between sources during result blending.
 **/
class ISourceSelector
{
protected:
    typedef SingleValueNumericAttribute<IntegerAttributeTemplate<int8_t> > SourceStore;
public:
    typedef std::unique_ptr<ISourceSelector> UP;
    typedef std::shared_ptr<ISourceSelector> SP;
    static const Source SOURCE_LIMIT = 254u;

    /**
     * Read-only interface to the data held by the parent source
     * selector.
     **/
    class Iterator {
    public:
        Iterator(const SourceStore & source)
            : _source(source)
        {
        }
        typedef std::unique_ptr<Iterator> UP;

        /**
         * Obtain the source to be used for the given document. This
         * function should always be called with increasing document
         * ids.
         *
         * @return source id
         * @param docId document id
         **/
        queryeval::Source getSource(uint32_t docId) const {
            return _source.getFast(docId);
        }

        /**
         * empty; defined for safe subclassing.
         **/
        virtual ~Iterator() {}

        uint32_t
        getDocIdLimit(void) const
        {
            return _source.getCommittedDocIdLimit();
        } 
    private:
        const SourceStore & _source;
    };

protected:
    ISourceSelector(Source defaultSource);
public:
    void setBaseId(uint32_t baseId) { _baseId = baseId; }
    uint32_t      getBaseId() const { return _baseId; }
    Source getDefaultSource() const { return _defaultSource; }
    /**
     * Set the source to be used for a given document.
     *
     * @param docId local document id
     * @param source source for this document
     **/
    virtual void setSource(uint32_t docId, Source source) = 0;

    /**
     * Gets the limit for docId numbers known to this selector.
     *
     * @return one above highest known doc id
     **/
    virtual uint32_t getDocIdLimit() const = 0;

    /**
     * Create a new iterator over the data held by this source
     * selector.
     *
     * @return source selection iterator
     **/
    virtual Iterator::UP createIterator() const = 0;

    /**
     * empty; defined for safe subclassing.
     **/
    virtual ~ISourceSelector() {}
private:
    uint32_t _baseId;
    Source   _defaultSource;
};

} // namespace queryeval
} // namespace search

