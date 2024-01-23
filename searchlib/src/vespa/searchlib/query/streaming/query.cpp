// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "query.h"
#include "hit_iterator_pack.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <cassert>

namespace search::streaming {

void
QueryConnector::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "Operator", _opName);
}

QueryConnector::QueryConnector(const char * opName) noexcept
    : QueryNode(),
      _opName(opName),
      _index(),
      _children()
{
}

void
QueryConnector::addChild(QueryNode::UP child) {
    _children.push_back(std::move(child));
}

QueryConnector::~QueryConnector() = default;

const HitList &
QueryConnector::evaluateHits(HitList & hl) const
{
    if (evaluate()) {
        hl.emplace_back(0, 0, 1, 1);
    }
    return hl;
}

void
QueryConnector::reset()
{
    for (const auto & node : _children) {
        node->reset();
    }
}

void
QueryConnector::getLeaves(QueryTermList & tl)
{
    for (const auto & node : _children) {
        node->getLeaves(tl);
    }
}

void
QueryConnector::getLeaves(ConstQueryTermList & tl) const
{
    for (const auto & node : _children) {
        node->getLeaves(tl);
    }
}

void
QueryConnector::getPhrases(QueryNodeRefList & tl)
{
    for (const auto & node : _children) {
        node->getPhrases(tl);
    }
}

void
QueryConnector::getPhrases(ConstQueryNodeRefList & tl) const
{
    for (const auto & node : _children) {
        node->getPhrases(tl);
    }
}

size_t
QueryConnector::depth() const
{
    size_t d(0);
    for (const auto & node : _children) {
        size_t t = node->depth();
        if (t > d) {
            d = t;
        }
    }
    return d+1;
}

size_t
QueryConnector::width() const
{
  size_t w(0);
  for (const auto & node : _children) {
    w += node->width();
  }

  return w;
}

std::unique_ptr<QueryConnector>
QueryConnector::create(ParseItem::ItemType type)
{
    switch (type) {
        case search::ParseItem::ITEM_AND:          return std::make_unique<AndQueryNode>();
        case search::ParseItem::ITEM_OR:
        case search::ParseItem::ITEM_WEAK_AND:     return std::make_unique<OrQueryNode>();
        case search::ParseItem::ITEM_EQUIV:        return std::make_unique<EquivQueryNode>();
        case search::ParseItem::ITEM_NOT:          return std::make_unique<AndNotQueryNode>();
        case search::ParseItem::ITEM_PHRASE:       return std::make_unique<PhraseQueryNode>();
        case search::ParseItem::ITEM_SAME_ELEMENT: return std::make_unique<SameElementQueryNode>();
        case search::ParseItem::ITEM_NEAR:         return std::make_unique<NearQueryNode>();
        case search::ParseItem::ITEM_ONEAR:        return std::make_unique<ONearQueryNode>();
        case search::ParseItem::ITEM_RANK:         return std::make_unique<RankWithQueryNode>();
        default: return nullptr;
    }
}

bool
TrueNode::evaluate() const
{
    return true;
}

bool FalseNode::evaluate() const {
    return false;
}

bool
AndQueryNode::evaluate() const
{
    for (const auto & qn : getChildren()) {
        if ( ! qn->evaluate() ) return false;
    }
    return true;
}

bool
AndNotQueryNode::evaluate() const {
    if (getChildren().empty()) return true;
    auto it = getChildren().begin();
    auto mt = getChildren().end();
    if ((*it)->evaluate()) {
        for (++it; it != mt; it++) {
            if ((*it)->evaluate()) return false;
        }
        return true;
    }
    return false;
}

bool
OrQueryNode::evaluate() const {
    for (const auto & qn : getChildren()) {
        if (qn->evaluate()) return true;
    }
    return false;
}

bool
RankWithQueryNode::evaluate() const {
    bool first = true;
    bool firstOk = false;
    for (const auto & qn : getChildren()) {
        if (qn->evaluate()) {
            if (first) firstOk = true;
        }
        first = false;
    }
    return firstOk;
}

bool
EquivQueryNode::evaluate() const
{
    return OrQueryNode::evaluate();
}

bool
SameElementQueryNode::evaluate() const {
    HitList hl;
    return ! evaluateHits(hl).empty();
}

void
SameElementQueryNode::addChild(QueryNode::UP child) {
    assert(dynamic_cast<const QueryTerm *>(child.get()) != nullptr);
    AndQueryNode::addChild(std::move(child));
}

const HitList &
SameElementQueryNode::evaluateHits(HitList & hl) const
{
    hl.clear();
    if ( !AndQueryNode::evaluate()) return hl;

    HitList tmpHL;
    const auto & children = getChildren();
    unsigned int numFields = children.size();
    unsigned int currMatchCount = 0;
    std::vector<unsigned int> indexVector(numFields, 0);
    auto curr = static_cast<const QueryTerm *> (children[currMatchCount].get());
    bool exhausted( curr->evaluateHits(tmpHL).empty());
    for (; !exhausted; ) {
        auto next = static_cast<const QueryTerm *>(children[currMatchCount+1].get());
        unsigned int & currIndex = indexVector[currMatchCount];
        unsigned int & nextIndex = indexVector[currMatchCount+1];

        const auto & currHit = curr->evaluateHits(tmpHL)[currIndex];
        uint32_t currElemId = currHit.element_id();

        const HitList & nextHL = next->evaluateHits(tmpHL);

        size_t nextIndexMax = nextHL.size();
        while ((nextIndex < nextIndexMax) && (nextHL[nextIndex].element_id() < currElemId)) {
            nextIndex++;
        }
        if ((nextIndex < nextIndexMax) && (nextHL[nextIndex].element_id() == currElemId)) {
            currMatchCount++;
            if ((currMatchCount+1) == numFields) {
                Hit h = nextHL[indexVector[currMatchCount]];
                hl.emplace_back(h.field_id(), h.element_id(), h.element_weight(), 0);
                currMatchCount = 0;
                indexVector[0]++;
            }
        } else {
            currMatchCount = 0;
            indexVector[currMatchCount]++;
        }
        curr = static_cast<const QueryTerm *>(children[currMatchCount].get());
        exhausted = (nextIndex >= nextIndexMax) || (indexVector[currMatchCount] >= curr->evaluateHits(tmpHL).size());
    }
    return hl;
}

bool
PhraseQueryNode::evaluate() const
{
  HitList hl;
  return ! evaluateHits(hl).empty();
}

void PhraseQueryNode::getPhrases(QueryNodeRefList & tl)            { tl.push_back(this); }
void PhraseQueryNode::getPhrases(ConstQueryNodeRefList & tl) const { tl.push_back(this); }

void
PhraseQueryNode::addChild(QueryNode::UP child) {
    assert(dynamic_cast<const QueryTerm *>(child.get()) != nullptr);
    AndQueryNode::addChild(std::move(child));
}

const HitList &
PhraseQueryNode::evaluateHits(HitList & hl) const
{
    hl.clear();
    _fieldInfo.clear();
    HitIteratorPack itr_pack(getChildren());
    if (!itr_pack.all_valid()) {
        return hl;
    }
    auto& last_child = dynamic_cast<const QueryTerm&>(*(*this)[size() - 1]);
    while (itr_pack.seek_to_matching_field_element()) {
        uint32_t first_position = itr_pack.front()->position();
        bool retry_element = true;
        while (retry_element) {
            uint32_t position_offset = 0;
            bool match = true;
            for (auto& it : itr_pack) {
                if (!it.seek_in_field_element(first_position + position_offset, itr_pack.get_field_element_ref())) {
                    retry_element = false;
                    match = false;
                    break;
                }
                if (it->position() > first_position + position_offset) {
                    first_position = it->position() - position_offset;
                    match = false;
                    break;
                }
                ++position_offset;
            }
            if (match) {
                auto h = *itr_pack.back();
                hl.push_back(h);
                auto& fi = last_child.getFieldInfo(h.field_id());
                updateFieldInfo(h.field_id(), hl.size() - 1, fi.getFieldLength());
                if (!itr_pack.front().step_in_field_element(itr_pack.get_field_element_ref())) {
                    retry_element = false;
                }
            }
        }
    }
    return hl;
}

void
PhraseQueryNode::updateFieldInfo(size_t fid, size_t offset, size_t fieldLength) const
{
    if (fid >= _fieldInfo.size()) {
        _fieldInfo.resize(fid + 1);
        // only set hit offset and field length the first time
        QueryTerm::FieldInfo & fi = _fieldInfo[fid];
        fi.setHitOffset(offset);
        fi.setFieldLength(fieldLength);
    }
    QueryTerm::FieldInfo & fi = _fieldInfo[fid];
    fi.setHitCount(fi.getHitCount() + 1);
}

bool
NearQueryNode::evaluate() const
{
    return AndQueryNode::evaluate();
}

void
NearQueryNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AndQueryNode::visitMembers(visitor);
    visit(visitor, "distance", static_cast<uint64_t>(_distance));
}


bool
ONearQueryNode::evaluate() const
{
  bool ok(NearQueryNode::evaluate());
  return ok;
}

Query::Query() = default;

Query::Query(const QueryNodeResultFactory & factory, vespalib::stringref queryRep)
    : _root()
{
    build(factory, queryRep);
}

bool
Query::evaluate() const {
    return valid() && _root->evaluate();
}

bool
Query::build(const QueryNodeResultFactory & factory, vespalib::stringref queryRep)
{
    search::SimpleQueryStackDumpIterator stack(queryRep);
    if (stack.next()) {
        _root = QueryNode::Build(nullptr, factory, stack, true);
    }
    return valid();
}

void
Query::getLeaves(QueryTermList & tl) {
    if (valid()) {
        _root->getLeaves(tl);
    }
}

void
Query::getLeaves(ConstQueryTermList & tl) const {
    if (valid()) {
        _root->getLeaves(tl);
    }
}

void
Query::getPhrases(QueryNodeRefList & tl) {
    if (valid()) {
        _root->getPhrases(tl);
    }
}

void
Query::getPhrases(ConstQueryNodeRefList & tl) const {
    if (valid()) {
        _root->getPhrases(tl);
    }
}

void
Query::reset() {
    if (valid()) {
        _root->reset();
    }
}

size_t
Query::depth() const {
    return valid() ? _root->depth() : 0;
}

size_t
Query::width() const {
    return valid() ? _root->width() : 0;
}

}
