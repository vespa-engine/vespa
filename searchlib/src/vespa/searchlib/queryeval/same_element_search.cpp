// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "same_element_search.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <functional>
#include <cassert>

using TFMD = search::fef::TermFieldMatchData;

namespace search::queryeval {

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
SameElementSearch::fetch_matching_elements(uint32_t docid, std::vector<uint32_t> & elems)
{
    _children.front()->getElementIds(docid, elems);
    for (auto it(_children.begin() + 1); it != _children.end();  it++) {
        (*it)->mergeElementIds(docid, elems);
    }
}

bool
SameElementSearch::check_element_match(uint32_t docid)
{
    _matchingElements.clear();
    fetch_matching_elements(docid, _matchingElements);
    return !_matchingElements.empty();
}

SameElementSearch::SameElementSearch(fef::TermFieldMatchData &tfmd,
                                     fef::MatchData::UP md,
                                     std::vector<ElementIterator::UP> children,
                                     bool strict)
    : _tfmd(tfmd),
      _md(std::move(md)),
      _children(std::move(children)),
      _strict(strict)
{
    _tfmd.reset(0);
    assert(!_children.empty());
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
SameElementSearch::doUnpack(uint32_t docid)
{
    _tfmd.resetOnlyDocId(docid);
}

void
SameElementSearch::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    SearchIterator::visitMembers(visitor);
    visit(visitor, "children", _children);
    visit(visitor, "strict", _strict);
}

void
SameElementSearch::find_matching_elements(uint32_t docid, std::vector<uint32_t> &dst)
{
    if (check_docid_match(docid)) {
        fetch_matching_elements(docid, dst);
    }
}

}
