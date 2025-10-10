// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "queryterm.hpp"
#include "query_term_data.h"
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/vespalib/objects/visit.h>
#include <algorithm>
#include <limits>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.query.streaming.queryterm");

using search::fef::IIndexEnvironment;
using search::fef::ITermData;
using search::fef::ITermFieldData;
using search::fef::MatchData;
using search::fef::TermFieldMatchData;
using search::fef::TermFieldMatchDataPosition;

namespace {

class CharInfo {
public:
    CharInfo();
    uint8_t get(uint8_t c) const noexcept { return _charInfo[c]; }
private:
    uint8_t _charInfo[256];
};

CharInfo::CharInfo()
    : _charInfo()
{
    // XXX: Should refactor to reduce number of magic constants.
    memset(_charInfo, 0x01, 128); // All 7 bits are ascii7bit
    memset(_charInfo+128, 0x00, 128); // The rest are not.
    memset(_charInfo + static_cast<uint8_t>('0'), 0x07, 10);
    _charInfo[uint8_t('-')] = 0x07;
    _charInfo[uint8_t('<')] = 0x07;
    _charInfo[uint8_t('>')] = 0x07;
    _charInfo[uint8_t(';')] = 0x07;
    _charInfo[uint8_t('[')] = 0x07;
    _charInfo[uint8_t(']')] = 0x07;

    _charInfo[uint8_t('.')] = 0x05;
    _charInfo[uint8_t('+')] = 0x05;
    _charInfo[uint8_t('e')] = 0x05;
    _charInfo[uint8_t('E')] = 0x05;
}

CharInfo G_charTable;

}

namespace search::streaming {

QueryTerm::~QueryTerm() = default;

void
QueryTerm::visitMembers(vespalib::ObjectVisitor & visitor) const
{
    QueryTermUCS4::visitMembers(visitor);
    visit(visitor, "encoding.isBase10Integer", _encoding.isBase10Integer());
    visit(visitor, "encoding.isFloat", _encoding.isFloat());
    visit(visitor, "encoding.isAscii7Bit", _encoding.isAscii7Bit());
    visit(visitor, "index", _index);
    visit(visitor, "weight", _weight.percent());
    visit(visitor, "uniqueid", _uniqueId);
}

QueryTerm::QueryTerm(Type type, string index, std::unique_ptr<NumericRangeSpec> range)
  : QueryTermUCS4(type, std::move(range)),
    _hitList(),
    _index(std::move(index)),
    _result(),
    _encoding(0x01),
    _isRanked(false),
    _filter(true),
    _weight(100),
    _uniqueId(0),
    _fieldInfo()
{
}

QueryTerm::QueryTerm(std::unique_ptr<QueryNodeResultBase> org, string_view termS, string indexS,
                     Type type, Normalizing normalizing)
    : QueryTermUCS4(QueryNormalization::optional_fold(termS, type, normalizing), type),
      _hitList(),
      _index(std::move(indexS)),
      _result(org.release()),
      _encoding(0x01),
      _isRanked(true),
      _filter(false),
      _weight(100),
      _uniqueId(0),
      _fieldInfo()
{
    if (!empty()) {
        uint8_t enc(0xff);
        for (char c : getTermString()) {
            enc &= G_charTable.get(c);
        }
        _encoding = EncodingBitMap(enc);
    }
}

void QueryTerm::getLeaves(QueryTermList & tl)                { tl.push_back(this); }
void QueryTerm::getLeaves(ConstQueryTermList & tl)     const { tl.push_back(this); }
bool QueryTerm::evaluate()                                   { return !_hitList.empty(); }
void QueryTerm::reset()                                      { _hitList.clear(); }
const HitList & QueryTerm::evaluateHits(HitList &)           { return _hitList; }

void
QueryTerm::get_element_ids(std::vector<uint32_t>& element_ids)
{
    get_element_ids_helper(element_ids, _hitList);
}

void QueryTerm::resizeFieldId(size_t fieldNo)
{
    if (fieldNo >= _fieldInfo.size()) {
        _fieldInfo.resize(std::max(32ul, fieldNo + 1));
    }
}

uint32_t
QueryTerm::add(uint32_t field_id, uint32_t element_id, int32_t element_weight, uint32_t position)
{
    uint32_t idx = _hitList.size();
    _hitList.emplace_back(field_id, element_id, element_weight, position);
    return idx;
}

void
QueryTerm::set_element_length(uint32_t hitlist_idx, uint32_t element_length)
{
    _hitList[hitlist_idx].set_element_length(element_length);
}

void
QueryTerm::unpack_match_data(uint32_t docid, MatchData& match_data, const IIndexEnvironment& index_env)
{
    if (isRanked()) {
        auto& qtd = static_cast<QueryTermData&>(getQueryItem());
        const ITermData& td = qtd.getTermData();
        unpack_match_data(docid, td, match_data, index_env);
    }
}

void
QueryTerm::unpack_match_data(uint32_t docid, const ITermData& td, MatchData& match_data, const IIndexEnvironment& index_env)
{
    HitList list;
    const HitList & hit_list = evaluateHits(list);
    unpack_match_data_helper(docid, td, match_data, hit_list, *this, is_filter(), index_env);
}

NearestNeighborQueryNode*
QueryTerm::as_nearest_neighbor_query_node() noexcept
{
    return nullptr;
}

MultiTerm*
QueryTerm::as_multi_term() noexcept
{
    return nullptr;
}

const MultiTerm*
QueryTerm::as_multi_term() const noexcept
{
    return nullptr;
}

RegexpTerm*
QueryTerm::as_regexp_term() noexcept
{
    return nullptr;
}

FuzzyTerm*
QueryTerm::as_fuzzy_term() noexcept
{
    return nullptr;
}

const EquivQueryNode*
QueryTerm::as_equiv_query_node() const noexcept
{
    return nullptr;
}

bool
QueryTerm::is_same_element_query_node() const noexcept
{
    return false;
}

SameElementQueryNode*
QueryTerm::as_same_element_query_node() noexcept
{
    return nullptr;
}

const SameElementQueryNode*
QueryTerm::as_same_element_query_node() const noexcept
{
    return nullptr;
}

void
QueryTerm::get_element_ids_helper(std::vector<uint32_t>& element_ids, const HitList& hit_list)
{
    if (!hit_list.empty()) { // only unpack if we have a hit
        bool need_sort = false;
        for (const auto &hit : hit_list) {
            if (element_ids.empty()) {
                element_ids.emplace_back(hit.element_id());
            } else if (element_ids.back() != hit.element_id()) {
                if (element_ids.back() > hit.element_id()) {
                    need_sort = true;
                }
                element_ids.emplace_back(hit.element_id());
            }
        }
        if (need_sort) {
            std::sort(element_ids.begin(), element_ids.end());
            element_ids.resize(std::unique(element_ids.begin(), element_ids.end()) - element_ids.begin());
        }
    }
}

}
