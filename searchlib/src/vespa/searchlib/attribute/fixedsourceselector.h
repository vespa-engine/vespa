// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributeguard.h"
#include "sourceselector.h"

namespace search {

class FixedSourceSelector : public SourceSelector
{
private:
    SourceStore & _source;
    queryeval::Source getSource(uint32_t docId) const {
        return _source.getFast(docId);
    }
    void reserve(uint32_t numDocs);

public:
    typedef std::unique_ptr<FixedSourceSelector> UP;
    class Iterator : public ISourceSelector::Iterator {
    private:
        AttributeGuard _attributeGuard;
    public:
        Iterator(const FixedSourceSelector & sourceSelector);
    };

public:
    FixedSourceSelector(queryeval::Source defaultSource,
                        const vespalib::string & attrBaseFileName,
                        uint32_t initialNumDocs = 0);
    virtual ~FixedSourceSelector();

    FixedSourceSelector::UP cloneAndSubtract(const vespalib::string & attrBaseFileName, uint32_t diff);
    static FixedSourceSelector::UP load(const vespalib::string & baseFileName);

    // Inherit doc from ISourceSelector
    virtual void setSource(uint32_t docId, queryeval::Source source);
    virtual uint32_t getDocIdLimit() const {
        return _source.getNumDocs() - 1;
    }
    virtual ISourceSelector::Iterator::UP createIterator() const {
        return ISourceSelector::Iterator::UP(new Iterator(*this));
    }
};

} // namespace search

