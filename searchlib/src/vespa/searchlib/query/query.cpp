// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "query.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search {

void QueryConnector::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "Operator", _opName);
}

QueryConnector::QueryConnector(const char * opName) :
  QueryNode(),
  _opName(opName),
  _index()
{
}

QueryConnector::~QueryConnector() = default;

const HitList & QueryConnector::evaluateHits(HitList & hl) const
{
    if (evaluate()) {
        hl.push_back(Hit(1, 0, 0, 1));
    }
    return hl;
}

void QueryConnector::reset()
{
    for(iterator it=begin(), mt=end(); it != mt; it++) {
        QueryNode & qn = **it;
        qn.reset();
    }
}

void QueryConnector::getLeafs(QueryTermList & tl)
{
  for(iterator it=begin(), mt=end(); it != mt; it++) {
    QueryNode & qn = **it;
    qn.getLeafs(tl);
  }
}

void QueryConnector::getLeafs(ConstQueryTermList & tl) const
{
  for(const_iterator it=begin(), mt=end(); it != mt; it++) {
    const QueryNode & qn = **it;
    qn.getLeafs(tl);
  }
}

void QueryConnector::getPhrases(QueryNodeRefList & tl)
{
  for(iterator it=begin(), mt=end(); it != mt; it++) {
    QueryNode & qn = **it;
    qn.getPhrases(tl);
  }
}

void QueryConnector::getPhrases(ConstQueryNodeRefList & tl) const
{
  for(const_iterator it=begin(), mt=end(); it != mt; it++) {
    const QueryNode & qn = **it;
    qn.getPhrases(tl);
  }
}

size_t QueryConnector::depth() const
{
  size_t d(0);
  for(const_iterator it=begin(), mt=end(); (it!=mt); it++) {
    const QueryNode & qn = **it;
    size_t t = qn.depth();
    if (t > d)
      d = t;
  }
  return d+1;
}

size_t QueryConnector::width() const
{
  size_t w(0);
  for(const_iterator it=begin(), mt=end(); (it!=mt); it++) {
    const QueryNode & qn = **it;
    w += qn.width();
  }

  return w;
}

QueryConnector *
QueryConnector::create(ParseItem::ItemType type)
{
    switch (type) {
        case search::ParseItem::ITEM_AND:          return new AndQueryNode();
        case search::ParseItem::ITEM_OR:           return new OrQueryNode();
        case search::ParseItem::ITEM_WEAK_AND:     return new OrQueryNode();
        case search::ParseItem::ITEM_EQUIV:        return new EquivQueryNode();
        case search::ParseItem::ITEM_WEIGHTED_SET: return new EquivQueryNode();
        case search::ParseItem::ITEM_DOT_PRODUCT:  return new OrQueryNode();
        case search::ParseItem::ITEM_WAND:         return new OrQueryNode();
        case search::ParseItem::ITEM_NOT:          return new AndNotQueryNode();
        case search::ParseItem::ITEM_PHRASE:       return new PhraseQueryNode();
        case search::ParseItem::ITEM_SAME_ELEMENT: return new AndQueryNode(); // TODO: This needs a same element operation to work for streaming search too.
        case search::ParseItem::ITEM_NEAR:         return new NearQueryNode();
        case search::ParseItem::ITEM_ONEAR:        return new ONearQueryNode();
        default:
            return nullptr;
    }
}

bool TrueNode::evaluate() const
{
    return true;
}

bool AndQueryNode::evaluate() const
{
  bool ok(true);
  for (const_iterator it=begin(), mt=end(); ok && (it!=mt); it++) {
    const QueryNode & qn = **it;
    ok = ok && qn.evaluate();
  }
  return ok;
}

bool AndNotQueryNode::evaluate() const
{
  bool ok(empty() ? true : front()->evaluate());
  if (!empty()) {
    for (const_iterator it=begin()+1, mt=end(); ok && (it!=mt); it++) {
      const QueryNode & qn = **it;
      ok = ok && ! qn.evaluate();
    }
  }
  return ok;
}

bool OrQueryNode::evaluate() const
{
  bool ok(false);
  for (const_iterator it=begin(), mt=end(); !ok && (it!=mt); it++) {
    const QueryNode & qn = **it;
    ok = qn.evaluate();
  }
  return ok;
}


bool EquivQueryNode::evaluate() const
{
    return OrQueryNode::evaluate();
}


bool PhraseQueryNode::evaluate() const
{
  bool ok(false);
  HitList hl;
  ok = ! evaluateHits(hl).empty();
  return ok;
}

void PhraseQueryNode::getPhrases(QueryNodeRefList & tl)            { tl.push_back(this); }
void PhraseQueryNode::getPhrases(ConstQueryNodeRefList & tl) const { tl.push_back(this); }

const HitList &
PhraseQueryNode::evaluateHits(HitList & hl) const
{
  hl.clear();
  _fieldInfo.clear();
  bool andResult(AndQueryNode::evaluate());
  if (andResult) {
    HitList tmpHL;
    unsigned int fullPhraseLen = size();
    unsigned int currPhraseLen = 0;
    std::vector<unsigned int> indexVector(fullPhraseLen, 0);
    const QueryTerm * curr = static_cast<const QueryTerm *> (&(*(*this)[currPhraseLen]));
    bool exhausted( curr->evaluateHits(tmpHL).empty());
    for (; !exhausted; ) {
      const QueryTerm & next = static_cast<const QueryTerm &>(*(*this)[currPhraseLen+1]);
      unsigned int & currIndex = indexVector[currPhraseLen];
      unsigned int & nextIndex = indexVector[currPhraseLen+1];

      size_t firstPosition = curr->evaluateHits(tmpHL)[currIndex].pos();
      uint32_t currElemId = curr->evaluateHits(tmpHL)[currIndex].elemId();
      uint32_t curContext = curr->evaluateHits(tmpHL)[currIndex].context();

      const HitList & nextHL = next.evaluateHits(tmpHL);

      int diff(0);
      size_t nextIndexMax = nextHL.size();
      while ((nextIndex < nextIndexMax) &&
              ((nextHL[nextIndex].context() < curContext) ||
               ((nextHL[nextIndex].context() == curContext) && (nextHL[nextIndex].elemId() <= currElemId))) &&
             ((diff = nextHL[nextIndex].pos()-firstPosition) < 1))
      {
        nextIndex++;
      }
      if ((diff == 1) && (nextHL[nextIndex].elemId() == currElemId)) {
        currPhraseLen++;
        bool ok = ((currPhraseLen+1)==fullPhraseLen);
        if (ok) {
          Hit h = nextHL[indexVector[currPhraseLen]];
          hl.push_back(h);
          const QueryTerm::FieldInfo & fi = next.getFieldInfo(h.context());
          updateFieldInfo(h.context(), hl.size() - 1, fi.getFieldLength());
          currPhraseLen = 0;
          indexVector[0]++;
        }
      } else {
        currPhraseLen = 0;
        indexVector[currPhraseLen]++;
      }
      curr = static_cast<const QueryTerm *>(&*(*this)[currPhraseLen]);
      exhausted = (nextIndex >= nextIndexMax) || (indexVector[currPhraseLen] >= curr->evaluateHits(tmpHL).size());
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

bool NotQueryNode::evaluate() const
{
  bool ok(false);
  for (const_iterator it=begin(), mt=end(); it!=mt; it++) {
    const QueryNode & qn = **it;
    ok |= ! qn.evaluate();
  }
  return ok;
}

bool NearQueryNode::evaluate() const
{
  bool ok(AndQueryNode::evaluate());
  return ok;
}

void NearQueryNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AndQueryNode::visitMembers(visitor);
    visit(visitor, "distance", _distance);
}


bool ONearQueryNode::evaluate() const
{
  bool ok(NearQueryNode::evaluate());
  return ok;
}

Query::Query() :
  _root()
{ }

Query::Query(const QueryNodeResultFactory & factory, const QueryPacketT & queryRep) :
  _root()
{
  build(factory, queryRep);
}

bool Query::evaluate() const
{
  bool ok = valid() ? _root->evaluate() : false;
  return ok;
}

bool Query::build(const QueryNodeResultFactory & factory, const QueryPacketT & queryRep)
{
    search::SimpleQueryStackDumpIterator stack(queryRep);
    if (stack.next()) {
        _root.reset(QueryNode::Build(NULL, factory, stack, true).release());
    }
    return valid();
}

void Query::getLeafs(QueryTermList & tl)
{
  if (valid()) {
    _root->getLeafs(tl);
  }
}

void Query::getLeafs(ConstQueryTermList & tl) const
{
  if (valid()) {
    _root->getLeafs(tl);
  }
}

void Query::getPhrases(QueryNodeRefList & tl)
{
  if (valid()) {
    _root->getPhrases(tl);
  }
}

void Query::getPhrases(ConstQueryNodeRefList & tl) const
{
  if (valid()) {
    _root->getPhrases(tl);
  }
}

void Query::reset()
{
  if (valid()) {
    _root->reset();
  }
}

size_t Query::depth() const
{
  return valid() ? _root->depth() : 0;
}

size_t Query::width() const
{
  return valid() ? _root->width() : 0;
}

}
