// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    using IIterator = queryeval::sourceselector::Iterator;
public:
    using UP = std::unique_ptr<FixedSourceSelector>;
    class Iterator : public IIterator {
    private:
        AttributeGuard _attributeGuard;
    public:
        Iterator(const FixedSourceSelector & sourceSelector);
    };

public:
    FixedSourceSelector(queryeval::Source defaultSource,
                        const vespalib::string & attrBaseFileName,
                        uint32_t initialNumDocs = 0);
    ~FixedSourceSelector() override;

    FixedSourceSelector::UP cloneAndSubtract(const vespalib::string & attrBaseFileName, uint32_t diff);
    static FixedSourceSelector::UP load(const vespalib::string & baseFileName, uint32_t currentId);

    // Inherit doc from ISourceSelector
    void setSource(uint32_t docId, queryeval::Source source) final override;
    uint32_t getDocIdLimit() const final override {
        return _source.getCommittedDocIdLimit() - 1;
    }
    void compactLidSpace(uint32_t lidLimit) override;
    std::unique_ptr<IIterator> createIterator() const final override {
        return std::make_unique<Iterator>(*this);
    }
};

} // namespace search

