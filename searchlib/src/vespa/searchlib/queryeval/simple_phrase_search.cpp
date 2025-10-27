// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_phrase_search.h"
#include "element_id_extractor.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/vespalib/objects/visit.h>
#include <functional>
#include <cassert>

using search::fef::TermFieldMatchData;
using std::unique_ptr;
using std::vector;
using vespalib::ObjectVisitor;

namespace search::queryeval {

namespace {
// Helper class
class PhraseMatcher {
    const fef::TermFieldMatchDataArray &_tmds;
    const vector<uint32_t> &_eval_order;
    vector<TermFieldMatchData::PositionsIterator> &_iterators;
    uint32_t _element_id;
    uint32_t _position;

    TermFieldMatchData::PositionsIterator &iterator(uint32_t word_index) {
        return _iterators[word_index];
    }

    TermFieldMatchData::PositionsIterator end(uint32_t word_index) {
        return _tmds[word_index]->end();
    }

    uint32_t elementId(uint32_t word_index) {
        return iterator(word_index)->getElementId();
    }

    uint32_t position(uint32_t word_index) {
        return iterator(word_index)->getPosition();
    }

    void iterateToElement(uint32_t word_index) {
        while (iterator(word_index) != end(word_index) &&
               elementId(word_index) < _element_id) {
            ++iterator(word_index);
        }
    }

    template <typename FwdIt>
    bool match(FwdIt first, FwdIt last) {
        if (first == last) {
            return true;
        }
        uint32_t word_index = *first;

        iterateToElement(word_index);
        while (iterator(word_index) != end(word_index) &&
               elementId(word_index) == _element_id) {
            if (position(word_index) == _position + word_index) {
                return match(++first, last);
            } else if (position(word_index) > _position + word_index) {
                return false;
            }
            ++iterator(word_index);
        }
        return false;
    }

    bool match() {
        _element_id = elementId(_eval_order[0]);
        if (position(_eval_order[0]) < _eval_order[0]) {
            // this position too early in element to allow match of other phrase terms
            return false;
        }
        _position = position(_eval_order[0]) - _eval_order[0];
        return match(++_eval_order.begin(), _eval_order.end());
    }

public:
    PhraseMatcher(const fef::TermFieldMatchDataArray &tmds,
                  const vector<uint32_t> &eval_order,
                  vector<TermFieldMatchData::PositionsIterator> &iterators)
        : _tmds(tmds),
          _eval_order(eval_order),
          _iterators(iterators),
          _element_id(0),
          _position(0)
    {
        for (size_t i = 0; i < _tmds.size(); ++i) {
            _iterators[i] = _tmds[i]->begin();
        }
    }

    bool hasMatch() {
        if (_tmds.size() == 1) {
            return true;
        }

        while (iterator(_eval_order[0]) != end(_eval_order[0])) {
            if (match()) {
                return true;
            }
            ++iterator(_eval_order[0]);
        }
        return false;
    }

    void fillPositions(TermFieldMatchData &tmd) {
        if (_tmds.size() == 1) {
            if (tmd.needs_normal_features()) {
                for (const fef::TermFieldMatchDataPosition & pos : *_tmds[0]) {
                    tmd.appendPosition(pos);
                }
            }
            if (tmd.needs_interleaved_features()) {
                tmd.setNumOccs(_tmds[0]->size());
                tmd.setFieldLength(_tmds[0]->getFieldLength());
            }
        } else {
            const bool needs_normal_features = tmd.needs_normal_features();
            uint32_t num_occs = 0;
            while (iterator(_eval_order[0]) != end(_eval_order[0])) {
                if (match()) {
                    if (needs_normal_features) {
                        tmd.appendPosition(*iterator(0));
                    }
                    ++num_occs;
                }
                ++iterator(_eval_order[0]);
            }
            if (tmd.needs_interleaved_features()) {
                tmd.setNumOccs(num_occs);
                tmd.setFieldLength(_tmds[0]->getFieldLength());
            }
        }
    }
};

bool
allTermsHaveMatch(const SimplePhraseSearch::Children &terms, const vector<uint32_t> &eval_order, uint32_t doc_id) {
    for (unsigned int order : eval_order) {
        if (!terms[order]->seek(doc_id)) {
            return false;
        }
    }
    return true;
}
}  // namespace

inline void
SimplePhraseSearch::phraseSeek(uint32_t doc_id) {
    if (allTermsHaveMatch(getChildren(), _eval_order, doc_id)) {
        matchPhrase(doc_id);
    }
}

void
SimplePhraseSearch::matchPhrase(uint32_t doc_id) {
    MultiSearch::doUnpack(doc_id);
    if (PhraseMatcher(_childMatch, _eval_order, _iterators).hasMatch()) {
        setDocId(doc_id);
    }
}


SimplePhraseSearch::SimplePhraseSearch(Children children,
                                       fef::MatchData::UP md,
                                       fef::TermFieldMatchDataArray childMatch,
                                       vector<uint32_t> eval_order,
                                       TermFieldMatchData &tmd, bool strict)
    : MultiSearch(std::move(children)),
      _md(std::move(md)),
      _childMatch(std::move(childMatch)),
      _eval_order(std::move(eval_order)),
      _tmd(tmd),
      _unpacked_docid(beginId()),
      _strict(strict),
      _iterators(getChildren().size())
{
    assert( ! getChildren().empty());
    assert(getChildren().size() == _childMatch.size());
    assert(getChildren().size() == _eval_order.size());
}

SimplePhraseSearch::~SimplePhraseSearch() = default;

void
SimplePhraseSearch::doSeek(uint32_t doc_id) {
    phraseSeek(doc_id);
    if (_strict) {
        doStrictSeek(doc_id);
    }
}

void
SimplePhraseSearch::doStrictSeek(uint32_t doc_id) {
    uint32_t next_candidate = doc_id;
    auto &best_child = *getChildren()[_eval_order[0]];
    while (getDocId() < doc_id) {
        best_child.seek(next_candidate + 1);
        next_candidate = best_child.getDocId();
        if (isAtEnd(next_candidate)) {
            setAtEnd();
            return;
        }
        phraseSeek(next_candidate);
    }
}

void
SimplePhraseSearch::doUnpack(uint32_t doc_id) {
    if (_unpacked_docid == doc_id) {
        return;
    }
    // All children have already been unpacked before this call is made.

    _tmd.reset(doc_id);
    PhraseMatcher(_childMatch, _eval_order, _iterators).fillPositions(_tmd);
    _unpacked_docid = doc_id;
}

void
SimplePhraseSearch::initRange(uint32_t begin_id, uint32_t end_id)
{
    MultiSearch::initRange(begin_id, end_id);
    _unpacked_docid = beginId();
}

void
SimplePhraseSearch::visitMembers(ObjectVisitor &visitor) const {
    MultiSearch::visitMembers(visitor);
    visit(visitor, "strict", _strict);
}

void
SimplePhraseSearch::get_element_ids(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    unpack(docid);
    ElementIdExtractor::get_element_ids(_tmd, docid, element_ids);
}

void
SimplePhraseSearch::and_element_ids_into(uint32_t docid, std::vector<uint32_t>& element_ids)
{
    unpack(docid);
    ElementIdExtractor::and_element_ids_into(_tmd, docid, element_ids);
}

}
