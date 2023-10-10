// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/singlenumericattribute.h>

namespace search::queryeval {

using Source = uint8_t;

namespace sourceselector {

class Iterator {
public:
    using SourceStore = SingleValueNumericAttribute<IntegerAttributeTemplate<int8_t> >;

    Iterator(const SourceStore & source) : _source(source) { }
    Iterator(const Iterator &) = delete;
    Iterator & operator = (const Iterator &) = delete;
    virtual ~Iterator() { }
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

    uint32_t getDocIdLimit() const {
        return _source.getCommittedDocIdLimit();
    }
private:
    const SourceStore & _source;
};

}

/**
 * Component used to select between sources during result blending.
 **/
class ISourceSelector
{
protected:
    using SourceStore = sourceselector::Iterator::SourceStore;
public:
    using UP = std::unique_ptr<ISourceSelector>;
    using SP = std::shared_ptr<ISourceSelector>;
    static const Source SOURCE_LIMIT = 254u;

protected:
    ISourceSelector(Source defaultSource);
public:
    void setBaseId(uint32_t baseId) { _baseId = baseId; }
    uint32_t      getBaseId() const { return _baseId; }
    void setDefaultSource(Source source);
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
     * Sets the lid limit in this selector.
     *
     * @param lidLimit the new lid limit (one above highest valid doc id).
     */
    virtual void compactLidSpace(uint32_t lidLimit) = 0;

    /**
     * Create a new iterator over the data held by this source
     * selector.
     *
     * @return source selection iterator
     **/
    virtual std::unique_ptr<sourceselector::Iterator> createIterator() const = 0;

    /**
     * empty; defined for safe subclassing.
     **/
    virtual ~ISourceSelector() {}
private:
    uint32_t _baseId;
    Source   _defaultSource;
};

}
