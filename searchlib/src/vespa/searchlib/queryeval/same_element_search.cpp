// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_search.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <functional>

using TFMD = search::fef::TermFieldMatchData;

namespace search::queryeval {

namespace {

template <typename It>
int32_t try_match(const fef::TermFieldMatchDataArray &match, std::vector<It> &iterators, uint32_t cand) {
    for (size_t i = 0; i < iterators.size(); ++i) {
        while ((iterators[i] != match[i]->end()) && (iterators[i]->getElementId() < cand)) {
            ++iterators[i];
        }
        if (iterators[i] == match[i]->end()) {
            return -1;
        }
        if (iterators[i]->getElementId() != cand) {
            return iterators[i]->getElementId();
        }
    }
    return cand;
}

}

bool
SameElementSearch::check_docid_match(uint32_t docid)
{
    for (const auto &child: _children) {
        if (!child->seek(docid)) {
            return false;
        }
    }
    return true;
}

void
SameElementSearch::unpack_children(uint32_t docid)
{
    for (const auto &child: _children) {
        child->doUnpack(docid);
    }
    for (size_t i = 0; i < _childMatch.size(); ++i) {
        _iterators[i] = _childMatch[i]->begin();
    }
}

bool
SameElementSearch::check_element_match(uint32_t docid)
{
    unpack_children(docid);
    int32_t cand = 0;
    int32_t next = try_match(_childMatch, _iterators, cand);
    while (next > cand) {
        cand = next;
        next = try_match(_childMatch, _iterators, cand);
    }
    return (cand == next);
}

SameElementSearch::SameElementSearch(fef::MatchData::UP md,
                                     std::vector<SearchIterator::UP> children,
                                     const fef::TermFieldMatchDataArray &childMatch,
                                     bool strict)
    : _md(std::move(md)),
      _children(std::move(children)),
      _childMatch(childMatch),
      _iterators(childMatch.size()),
      _strict(strict)
{
    assert(!_children.empty());
    assert(_childMatch.valid());
}

void
SameElementSearch::initRange(uint32_t begin_id, uint32_t end_id)
{
    SearchIterator::initRange(begin_id, end_id);
    for (const auto &child: _children) {
        child->initRange(begin_id, end_id);
    }
}

void
SameElementSearch::doSeek(uint32_t docid) {
    if (check_docid_match(docid) && check_element_match(docid)) {
        setDocId(docid);
    } else if (_strict) {
        docid = std::max(docid + 1, _children[0]->getDocId());
        while (!isAtEnd(docid)) {
            if (check_docid_match(docid) && check_element_match(docid)) {
                setDocId(docid);
                return;
            }
            docid = std::max(docid + 1, _children[0]->getDocId());
        }
        setAtEnd();
    }
}

void
SameElementSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    SearchIterator::visitMembers(visitor);
    visit(visitor, "children", _children);
    visit(visitor, "strict", _strict);
}

}
