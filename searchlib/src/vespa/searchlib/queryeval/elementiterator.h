// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "searchiterator.h"

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class ElementIterator {
public:
    using UP = std::unique_ptr<ElementIterator>;
    ElementIterator(SearchIterator::UP search) : _search(std::move(search)) { }
    virtual ~ElementIterator() = default;
    bool seek(uint32_t docId) {
        return _search->seek(docId);
    }
    void initFullRange() {
        _search->initFullRange();
    }
    void initRange(uint32_t beginid, uint32_t endid) {
        _search->initRange(beginid, endid);
    }
    uint32_t getDocId() const {
        return _search->getDocId();
    }
    virtual void getElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) = 0;
    virtual void mergeElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) = 0;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
protected:
    SearchIterator::UP _search;
};

class ElementIteratorWrapper : public ElementIterator {
public:
    ElementIteratorWrapper(SearchIterator::UP search, fef::TermFieldMatchData & tfmd);
    ~ElementIteratorWrapper() override;
    void getElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) override;
    void mergeElementIds(uint32_t docId, std::vector<uint32_t> & elementIds) override;
private:
    fef::TermFieldMatchData & _tfmd;
};

}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::ElementIterator &obj);
void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::ElementIterator *obj);
