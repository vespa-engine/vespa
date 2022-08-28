// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multisearch.h"
#include <vespa/vespalib/objects/visit.hpp>
#include <cassert>

namespace search::queryeval {

void
MultiSearch::insert(size_t index, SearchIterator::UP search)
{
    assert(index <= _children.size());
    _children.insert(_children.begin()+index, std::move(search));
    onInsert(index);
}

SearchIterator::UP
MultiSearch::remove(size_t index)
{
    assert(index < _children.size());
    SearchIterator::UP search = std::move(_children[index]);
    _children.erase(_children.begin() + index);
    onRemove(index);
    return search;
}

void
MultiSearch::doUnpack(uint32_t docid)
{
    for (auto &child: _children) {
        if (__builtin_expect(child->getDocId() < docid, false)) {
            child->doSeek(docid);
        }
        if (__builtin_expect(child->getDocId() == docid, false)) {
            child->doUnpack(docid);
        }
    }
}

MultiSearch::MultiSearch(Children children)
    : _children(std::move(children))
{
}

MultiSearch::MultiSearch() = default;
MultiSearch::~MultiSearch() = default;

void
MultiSearch::initRange(uint32_t beginid, uint32_t endid)
{
    SearchIterator::initRange(beginid, endid);
    for (auto & child : _children) {
        child->initRange(beginid, endid);
    }
}

void
MultiSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "children", _children);
}

}
