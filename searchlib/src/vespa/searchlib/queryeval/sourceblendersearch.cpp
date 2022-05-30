// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sourceblendersearch.h"
#include "isourceselector.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <vespa/vespalib/util/array.hpp>

namespace search::queryeval {

EmptySearch SourceBlenderSearch::_emptySearch;

class SourceBlenderSearchNonStrict : public SourceBlenderSearch
{
public:
    SourceBlenderSearchNonStrict(std::unique_ptr<Iterator> sourceSelector, const Children &children)
        : SourceBlenderSearch(std::move(sourceSelector), children)
    {}
};

class SourceBlenderSearchStrict : public SourceBlenderSearch
{
public:
    SourceBlenderSearchStrict(std::unique_ptr<Iterator> sourceSelector, const Children &children);
private:
    VESPA_DLL_LOCAL void advance() __attribute__((noinline));
    vespalib::Array<SearchIterator *>  _nextChildren;

    void doSeek(uint32_t docid) override;
    Trinary is_strict() const override { return Trinary::True; }
};

SourceBlenderSearchStrict::SourceBlenderSearchStrict(
        std::unique_ptr<Iterator> sourceSelector,
        const Children &children)
    : SourceBlenderSearch(std::move(sourceSelector), children),
      _nextChildren()
{
    _nextChildren.reserve(children.size());
}

void
SourceBlenderSearch::doSeek(uint32_t docid)
{
    if (docid >= _docIdLimit) {
        setDocId(endDocId);
        return;
    }
    _matchedChild = getSearch(_sourceSelector->getSource(docid));
    if (_matchedChild->seek(docid)) {
        setDocId(docid);
    }
}

void
SourceBlenderSearchStrict::doSeek(uint32_t docid)
{
    if (docid >= _docIdLimit) {
        setDocId(endDocId);
        return;
    }
    _matchedChild = getSearch(_sourceSelector->getSource(docid));
    if (_matchedChild->seek(docid)) {
        setDocId(docid);
    } else {
        for (auto & child : _children) {
            getSearch(child)->seek(docid);
        }
        advance();
    }
}

void
SourceBlenderSearchStrict::advance()
{
    for (;;) {
        SearchIterator * search = getSearch(_children[0]);
        uint32_t minNextId = search->getDocId();
        _nextChildren.clear();
        _nextChildren.push_back_fast(search);
        for (uint32_t i = 1; i < _children.size(); ++i) {
            search = getSearch(_children[i]);
            uint32_t nextId = search->getDocId();
            if (nextId < minNextId) {
                minNextId = nextId;
                _nextChildren.clear();
                _nextChildren.push_back_fast(search);
            } else if (nextId == minNextId) {
                _nextChildren.push_back_fast(search);
            }
        }
        if (isAtEnd(minNextId)) {
            setAtEnd();
            return;
        }
        if (minNextId >= _docIdLimit) {
            setAtEnd();
            return;
        }
        search = getSearch(_sourceSelector->getSource(minNextId));
        for (SearchIterator * child : _nextChildren) {
            if (child == search) {
                _matchedChild = search;
                setDocId(minNextId);
                return;
            }
            child->seek(minNextId + 1);
        }
    }
}

void
SourceBlenderSearch::doUnpack(uint32_t docid)
{
    _matchedChild->doUnpack(docid);
}

SourceBlenderSearch::SourceBlenderSearch(
        std::unique_ptr<sourceselector::Iterator> sourceSelector,
        const Children &children) :
    _matchedChild(nullptr),
    _sourceSelector(std::move(sourceSelector)),
    _children(),
    _docIdLimit(_sourceSelector->getDocIdLimit())
{
    for (size_t i(0); i < sizeof(_sources)/sizeof(_sources[0]); i++) {
        _sources[i] = &_emptySearch;
    }
    for (auto & child : children) {
        Source sid(child.sourceId);
        _children.push_back(sid);
        _sources[sid] = child.search;
    }
}

void
SourceBlenderSearch::initRange(uint32_t beginid, uint32_t endid)
{
    SearchIterator::initRange(beginid, endid);
    for (auto & child : _children) {
        getSearch(child)->initRange(beginid, endid);
    }
}

void
SourceBlenderSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "children", _children);
    for (const auto & child : _children) {
        vespalib::asciistream os;
        os << "Source " << child;
        visit(visitor, os.str(), *getSearch(child));
    }
}

SourceBlenderSearch::~SourceBlenderSearch()
{
    for (auto & child : _children) {
        delete getSearch(child);
    }
}

void
SourceBlenderSearch::setChild(size_t index, SearchIterator::UP child) {
    assert(_sources[_children[index]] == nullptr);
    _sources[_children[index]] = child.release();
}

SearchIterator::UP
SourceBlenderSearch::create(std::unique_ptr<sourceselector::Iterator> sourceSelector,
                            const Children &children, bool strict)
{
    if (strict) {
        return std::make_unique<SourceBlenderSearchStrict>(std::move(sourceSelector), children);
    } else {
        return std::make_unique<SourceBlenderSearchNonStrict>(std::move(sourceSelector), children);
    }
}

}

void visit(vespalib::ObjectVisitor &self, const vespalib::string &name,
           const search::queryeval::SourceBlenderSearch::Child &obj)
{
    self.openStruct(name, "search::queryeval::SourceBlenderSearch::Child");
    visit(self, "search",   obj.search);
    visit(self, "sourceId", obj.sourceId);
    self.closeStruct();
}
