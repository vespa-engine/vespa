// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nearsearch.h"
#include "i_element_gap_inspector.h"
#include "near_search_utils.h"
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <cassert>
#include <map>

#include <vespa/log/log.h>
LOG_SETUP(".nearsearch");

using search::queryeval::IElementGapInspector;
using search::queryeval::near_search_utils::BoolMatchResult;
using search::queryeval::near_search_utils::ElementIdMatchResult;

namespace search::queryeval {

namespace {

using search::fef::ElementGap;
using search::fef::TermFieldMatchDataArray;
using search::fef::TermFieldMatchDataPosition;
using search::fef::TermFieldMatchDataPositionKey;

template<typename T>
void setup_fields(uint32_t window, const IElementGapInspector& element_gap_inspector,
                  std::vector<T> &matchers, const TermFieldMatchDataArray &in, uint32_t terms) {
    std::map<uint32_t,uint32_t> fields;
    for (size_t i = 0; i < in.size(); ++i) {
        ++fields[in[i]->getFieldId()];
    }
    for (auto [field, cnt]: fields) {
        if (cnt == terms) {
            matchers.push_back(T(window, element_gap_inspector.get_element_gap(field), field, in));
        }
    }
}

TermFieldMatchDataPositionKey
calc_window_end_pos(const TermFieldMatchDataPosition& pos, uint32_t window, ElementGap element_gap)
{
    if (!element_gap.has_value() || pos.getElementLen() + element_gap.value() > pos.getPosition() + window) {
        return { pos.getElementId(), pos.getPosition() + window };
    } else {
        return { pos.getElementId() + 1, pos.getPosition() + window - pos.getElementLen() - element_gap.value() };
    }
}

} // namespace search::queryeval::<unnamed>

NearSearchBase::NearSearchBase(Children terms,
                               const TermFieldMatchDataArray &data,
                               uint32_t window,
                               bool strict)
    : AndSearch(std::move(terms)),
      _data_size(data.size()),
      _window(window),
      _strict(strict)
{
}

void
NearSearchBase::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AndSearch::visitMembers(visitor);
    visit(visitor, "data_size", _data_size);
    visit(visitor, "window", _window);
    visit(visitor, "strict", _strict);
}

void
NearSearchBase::seekNext(uint32_t docId)
{
    LOG(debug, "seekNext(%d)", docId);
    const Children & terms(getChildren());
    SearchIterator &firstTerm = *terms[0];
    uint32_t nextId = firstTerm.getDocId();
    while ( ! isAtEnd(nextId)) {
        LOG(debug, "Looking for match in document %d.", nextId);
        bool foundHit = true;
        for (uint32_t i = 1, len = terms.size(); i < len; ++i) {
            SearchIterator &term = *terms[i];
            if (!term.seek(nextId)) {
                LOG(debug, "Term %d does not occur in document %d.", i, nextId);
                foundHit = false;
                if (term.getDocId() > nextId) {
                    nextId = term.getDocId();
                    LOG(debug, "Next document in which term %d occurs is %d.", i, nextId);
                } else {
                    ++nextId;
                    LOG(debug, "Bumping target document to %d.", nextId);
                }
                break;
            }
            LOG(debug, "Term %d occurs in document %d.", i, nextId);
        }
        if (foundHit) {
            LOG(debug, "All terms occur in document %d, check for match.", nextId);
            if (match(nextId)) {
                LOG(debug, "Document %d matches.", nextId);
                break;
            }
            ++nextId;
        }
        if ( ! isAtEnd(nextId)) {
            LOG(debug, "Seeking next document that contains term 0, starting at %d.", nextId);
            firstTerm.seek(nextId);
            nextId = firstTerm.getDocId();
            LOG(debug, "Next document that contains term 0 is %d.", nextId);
        }
    }
    if (isAtEnd(nextId)) {
        LOG(debug, "Reached end of document list.");
        setAtEnd();
    } else {
        setDocId(nextId);
    }
}

void
NearSearchBase::doSeek(uint32_t docId)
{
    LOG(debug, "doSeek(%d)", docId);
    const Children & terms(getChildren());
    bool foundHit = true;
    for (uint32_t i = 0, len = terms.size(); i < len; ++i) {
        if (! terms[i]->seek(docId)) {
            LOG(debug, "Term %d does not occur in document %d.", i, docId);
            foundHit = false;
            break;
        }
    }
    if (foundHit && match(docId)) {
        LOG(debug, "Document %d matches.", docId);
        setDocId(docId);
    } else if (_strict) {
        LOG(debug, "Document %d does not match, seeking next.", docId);
        seekNext(docId);
    }
}

NearSearch::NearSearch(Children terms,
                       const TermFieldMatchDataArray &data,
                       uint32_t window,
                       const IElementGapInspector& element_gap_inspector,
                       bool strict)
    : NearSearchBase(std::move(terms), data, window, strict),
      _matchers()
{
    setup_fields(window, element_gap_inspector, _matchers, data, getChildren().size());
}

NearSearch::~NearSearch() = default;

namespace {

struct PosIter {
    search::fef::TermFieldMatchData::PositionsIterator curPos;
    search::fef::TermFieldMatchData::PositionsIterator endPos;

    bool operator< (const PosIter &other) const {
        // assumes none is at end
        TermFieldMatchDataPositionKey mykey = *curPos;
        TermFieldMatchDataPositionKey otherkey = *other.curPos;
        return mykey < otherkey;
    }
};

struct Iterators
{
    vespalib::PriorityQueue<PosIter> _queue;
    TermFieldMatchDataPositionKey    _maxOcc;
    ElementGap                       _element_gap;

    Iterators(ElementGap element_gap)
        : _queue(),
          _maxOcc(),
          _element_gap(element_gap)
    {
    }
    void update(TermFieldMatchDataPositionKey occ)
    {
        if (_queue.size() == 1 || _maxOcc < occ) { _maxOcc = occ; }
    }

    void add(const search::fef::TermFieldMatchData *term)
    {
        PosIter iter;
        iter.curPos = term->begin();
        iter.endPos = term->end();
        LOG_ASSERT(iter.curPos != iter.endPos);
        _queue.push(iter);
        update(*iter.curPos);
    }

    template <typename MatchResult>
    void match(uint32_t window, MatchResult& match_result) {
        for (;;) {
            PosIter &front = _queue.front();
            auto lastAllowed = calc_window_end_pos(*front.curPos, window, _element_gap);

            if (!(lastAllowed < _maxOcc)) {
                match_result.register_match(front.curPos->getElementId());
                if constexpr (MatchResult::shortcut_return) {
                    return;
                }
            }
            do {
                ++front.curPos;
                if (front.curPos == front.endPos) {
                    return;
                }
                lastAllowed = calc_window_end_pos(*front.curPos, window, _element_gap);
            } while (lastAllowed < _maxOcc);

            update(*front.curPos);
            _queue.adjust();
        }
    }
};

} // namespace <unnamed>

template <typename MatchResult>
void
NearSearch::Matcher::match(uint32_t docId, MatchResult& match_result)
{
    Iterators pos(get_element_gap());
    for (uint32_t i = 0, len = inputs().size(); i < len; ++i) {
        const search::fef::TermFieldMatchData *term = inputs()[i];
        if (term->getDocId() != docId || term->begin() == term->end()) {
            LOG(debug, "No occurrences found for term %d.", i);
            return;
        }
        LOG(debug, "Got positions iterator for term %d.", i);
        pos.add(term);
    }

    // Look for matching window.
    pos.match(window(), match_result);
}

bool
NearSearch::match(uint32_t docId)
{
    // Retrieve position iterators for each term.
    doUnpack(docId);
    BoolMatchResult match_result;
    for (size_t i = 0; i < _matchers.size(); ++i) {
        _matchers[i].match(docId, match_result);
        if (match_result.is_match()) {
            return true;
        }
    }
    return false;
}

void
NearSearch::get_element_ids(uint32_t docId, std::vector<uint32_t>& element_ids)
{
    // Retrieve the elements that matched
    assert(element_ids.empty());
    ElementIdMatchResult match_result(element_ids);
    for (auto& matcher : _matchers) {
        matcher.match(docId, match_result);
    }
    match_result.maybe_sort_element_ids();
}

ONearSearch::ONearSearch(Children terms,
                         const TermFieldMatchDataArray &data,
                         uint32_t window,
                         const IElementGapInspector& element_gap_inspector,
                         bool strict)
    : NearSearchBase(std::move(terms), data, window, strict),
      _matchers()
{
    setup_fields(window, element_gap_inspector, _matchers, data, getChildren().size());
}

ONearSearch::~ONearSearch() = default;

template <typename MatchResult>
void
ONearSearch::Matcher::match(uint32_t docId, MatchResult& match_result)
{
    uint32_t numTerms = inputs().size();
    PositionsIteratorList pos;
    for (uint32_t i = 0; i < numTerms; ++i) {
        const search::fef::TermFieldMatchData *term = inputs()[i];
        if (term->getDocId() != docId || term->begin() == term->end()) {
            LOG(debug, "No occurrences found for term %d.", i);
            return;
        }
        LOG(debug, "Got positions iterator for term %d.", i);
        pos.push_back(term->begin());
    }
    if (numTerms < 2) {
        for ( ; pos[0] != inputs()[0]->end(); ++pos[0]) {
            match_result.register_match(pos[0]->getElementId());
            if constexpr (MatchResult::shortcut_return) {
                return;
            }
        }
        return; // 1 term is always near itself
    }

    int32_t remain = window();

    TermFieldMatchDataPositionKey prevTermPos;
    TermFieldMatchDataPositionKey curTermPos;
    TermFieldMatchDataPositionKey lastAllowed;

    // Look for match for every occurrence of the first term.
    for ( ; pos[0] != inputs()[0]->end(); ++pos[0]) {
        TermFieldMatchDataPositionKey firstTermPos = *pos[0];
        lastAllowed = calc_window_end_pos(*pos[0], remain, get_element_gap());
        if (lastAllowed < curTermPos) {
            // if we already know that we must seek onwards:
            continue;
        }
        prevTermPos = firstTermPos;
        LOG(spam, "Looking for match in window [%d:%d, %d:%d].",
            firstTermPos.getElementId(), firstTermPos.getPosition(), lastAllowed.getElementId(), lastAllowed.getPosition());
        for (uint32_t i = 1; i < numTerms; ++i) {
            LOG(spam, "Forwarding iterator for term %d beyond %d.", i, prevTermPos.getPosition());
            while (pos[i] != inputs()[i]->end() && !(prevTermPos < *pos[i])) {
                ++pos[i];
            }
            if (pos[i] == inputs()[i]->end()) {
                LOG(debug, "Reached end of occurrences for term %d without matching ONEAR.", i);
                return;
            }
            curTermPos = *pos[i];
            if (lastAllowed < curTermPos) {
                // outside window
                break;
            }
            LOG(spam, "Current position for term %d is %d.", i, curTermPos.getPosition());
            if (i + 1 == numTerms) {
                LOG(debug, "ONEAR match found for document %d.", docId);
                // OK for all terms
                match_result.register_match(firstTermPos.getElementId());
                if constexpr (MatchResult::shortcut_return) {
                    return;
                }
                break;
            }
            prevTermPos = curTermPos;
        }
    }
    if constexpr (MatchResult::shortcut_return) {
        LOG(debug, "No ONEAR match found for document %d.", docId);
    }
}

bool
ONearSearch::match(uint32_t docId)
{
    // Retrieve position iterators for each term.
    doUnpack(docId);
    BoolMatchResult match_result;
    for (auto& matcher : _matchers) {
        matcher.match(docId, match_result);
        if (match_result.is_match()) {
            return true;
        }
    }
    return false;
}

void
ONearSearch::get_element_ids(uint32_t docId, std::vector<uint32_t>& element_ids)
{
    // Retrieve the elements that matched
    assert(element_ids.empty());
    ElementIdMatchResult match_result(element_ids);
    for (auto& matcher : _matchers) {
        matcher.match(docId, match_result);
    }
    match_result.maybe_sort_element_ids();
}

}
