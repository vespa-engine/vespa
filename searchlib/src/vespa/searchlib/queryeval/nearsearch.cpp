// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "nearsearch.h"
#include <vespa/vespalib/objects/visit.h>
#include <vespa/vespalib/util/priority_queue.h>
#include <limits>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".nearsearch");

namespace search::queryeval {

namespace {

using search::fef::TermFieldMatchDataArray;
using search::fef::TermFieldMatchDataPositionKey;

template<typename T>
void setup_fields(uint32_t window, std::vector<T> &matchers, const TermFieldMatchDataArray &in) {
    std::set<uint32_t> fields;
    for (size_t i = 0; i < in.size(); ++i) {
        fields.insert(in[i]->getFieldId());
    }
    std::set<uint32_t>::const_iterator pos = fields.begin();
    std::set<uint32_t>::const_iterator end = fields.end();
    for (; pos != end; ++pos) {
        matchers.push_back(T(window, *pos, in));
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
                       bool strict)
    : NearSearchBase(std::move(terms), data, window, strict),
      _matchers()
{
    setup_fields(window, _matchers, data);
}

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
    TermFieldMatchDataPositionKey _maxOcc;

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

    bool match(uint32_t window) {
        for (;;) {
            PosIter &front = _queue.front();
            TermFieldMatchDataPositionKey lastAllowed = *front.curPos;
            lastAllowed.setPosition(front.curPos->getPosition() + window);

            if (!(lastAllowed < _maxOcc)) {
                return true;
            }
            do {
                ++front.curPos;
                if (front.curPos == front.endPos) {
                    return false;
                }
                lastAllowed = *front.curPos;
                lastAllowed.setPosition(front.curPos->getPosition() + window);
            } while (lastAllowed < _maxOcc);

            update(*front.curPos);
            _queue.adjust();
        }
    }
};

} // namespace <unnamed>

bool
NearSearch::Matcher::match(uint32_t docId)
{
    Iterators pos;
    for (uint32_t i = 0, len = inputs().size(); i < len; ++i) {
        const search::fef::TermFieldMatchData *term = inputs()[i];
        if (term->getDocId() != docId || term->begin() == term->end()) {
            LOG(debug, "No occurrences found for term %d.", i);
            return false;
        }
        LOG(debug, "Got positions iterator for term %d.", i);
        pos.add(term);
    }

    // Look for matching window.
    return pos.match(window());
}

bool
NearSearch::match(uint32_t docId)
{
    // Retrieve position iterators for each term.
    doUnpack(docId);
    for (size_t i = 0; i < _matchers.size(); ++i) {
        if (_matchers[i].match(docId)) {
            return true;
        }
    }
    return false;
}

ONearSearch::ONearSearch(Children terms,
                         const TermFieldMatchDataArray &data,
                         uint32_t window,
                         bool strict)
    : NearSearchBase(std::move(terms), data, window, strict),
      _matchers()
{
    setup_fields(window, _matchers, data);
}

bool
ONearSearch::Matcher::match(uint32_t docId)
{
    uint32_t numTerms = inputs().size();
    PositionsIteratorList pos;
    for (uint32_t i = 0; i < numTerms; ++i) {
        const search::fef::TermFieldMatchData *term = inputs()[i];
        if (term->getDocId() != docId || term->begin() == term->end()) {
            LOG(debug, "No occurrences found for term %d.", i);
            return false;
        }
        LOG(debug, "Got positions iterator for term %d.", i);
        pos.push_back(term->begin());
    }
    if (numTerms < 2) return true; // 1 term is always near itself

    int32_t remain = window();

    TermFieldMatchDataPositionKey prevTermPos;
    TermFieldMatchDataPositionKey curTermPos;
    TermFieldMatchDataPositionKey lastAllowed;

    // Look for match for every occurrence of the first term.
    for ( ; pos[0] != inputs()[0]->end(); ++pos[0]) {
        TermFieldMatchDataPositionKey firstTermPos = *pos[0];
        lastAllowed = firstTermPos;
        lastAllowed.setPosition(firstTermPos.getPosition() + remain);
        if (lastAllowed < curTermPos) {
            // if we already know that we must seek onwards:
            continue;
        }
        prevTermPos = firstTermPos;
        LOG(spam, "Looking for match in window [%d, %d].",
            firstTermPos.getPosition(), lastAllowed.getPosition());
        for (uint32_t i = 1; i < numTerms; ++i) {
            LOG(spam, "Forwarding iterator for term %d beyond %d.", i, prevTermPos.getPosition());
            while (pos[i] != inputs()[i]->end() && !(prevTermPos < *pos[i])) {
                ++pos[i];
            }
            if (pos[i] == inputs()[i]->end()) {
                LOG(debug, "Reached end of occurrences for term %d without matching ONEAR.", i);
                return false;
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
                return true;
            }
            prevTermPos = curTermPos;
        }
    }
    LOG(debug, "No ONEAR match found for document %d.", docId);
    return false;
}

bool
ONearSearch::match(uint32_t docId)
{
    // Retrieve position iterators for each term.
    doUnpack(docId);
    for (size_t i = 0; i < _matchers.size(); ++i) {
        if (_matchers[i].match(docId)) {
            return true;
        }
    }
    return false;
}

}
