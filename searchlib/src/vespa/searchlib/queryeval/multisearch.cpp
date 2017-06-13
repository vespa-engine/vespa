// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multisearch.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search {
namespace queryeval {

void
MultiSearch::insert(size_t index, SearchIterator::UP search)
{
    assert(index <= _children.size());
    _children.insert(_children.begin()+index, search.release());
    onInsert(index);
}

SearchIterator::UP
MultiSearch::remove(size_t index)
{
    assert(index < _children.size());
    SearchIterator::UP search(_children[index]);
    _children.erase(_children.begin() + index);
    onRemove(index);
    return search;
}

void
MultiSearch::doUnpack(uint32_t docid)
{
    size_t sz(_children.size());
    for (size_t i = 0; i < sz; ) {
        if (__builtin_expect(_children[i]->getDocId() < docid, false)) {
            _children[i]->doSeek(docid);
            if (_children[i]->isAtEnd()) {
                sz = deactivate(i);
                continue;
            }
        }
        if (__builtin_expect(_children[i]->getDocId() == docid, false)) {
            _children[i]->doUnpack(docid);
        }
        i++;
    }
}

size_t
MultiSearch::deactivate(size_t idx)
{
    assert(idx < _children.size());
    delete _children[idx];
    _children[idx] = _children.back();
    _children.pop_back();
    return _children.size();
}

MultiSearch::MultiSearch(const Children & children)
    : _children(children)
{
}

MultiSearch::~MultiSearch()
{
    for (SearchIterator * child : _children) {
        delete child;
    }
}

void
MultiSearch::initRange(uint32_t beginid, uint32_t endid)
{
    SearchIterator::initRange(beginid, endid);
    for (SearchIterator * child : _children) {
        child->initRange(beginid, endid);
    }
}

void
MultiSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "children", _children);
}


} // namespace queryeval
} // namespace search
