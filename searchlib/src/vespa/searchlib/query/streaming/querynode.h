// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hit.h"
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search { class SimpleQueryStackDumpIterator; }

namespace search::streaming {

class QueryTerm;
class QueryNode;
class QueryNodeResultFactory;

/// Typedef a simple list that contains references to QueryNodes.
typedef std::vector<QueryNode *> QueryNodeRefList;
/// Typedef a simple list that contains const references to QueryNodes.
typedef std::vector<const QueryNode *> ConstQueryNodeRefList;
/// Typedef a simple list that contains references to QueryTerms.
typedef std::vector<QueryTerm *> QueryTermList;
/// Typedef a simple list that contains const references to QueryTerms.
typedef std::vector<const QueryTerm *> ConstQueryTermList;

/**
  This is the base of any node in the query tree. Both leaf nodes (terms)
  and operator nodes (AND, NOT, OR, PHRASE, NEAR, ONEAR, etc).
*/
class QueryNode
{
 public:
  typedef std::unique_ptr<QueryNode> UP;

  virtual ~QueryNode() { }
  /// This evalutes if the subtree starting here evaluates to true.
  virtual bool evaluate() const = 0;
  /// This return the hitList for this subtree. Does only give meaning in a
  /// phrase search or any other search that requires position info.
  virtual const HitList & evaluateHits(HitList & hl) const;
  /// Clears all the hitlists so the query tree can be reused.
  virtual void reset() = 0;
  /// Gives you all leafs of this tree.
  virtual void getLeafs(QueryTermList & tl) = 0;
  /// Gives you all leafs of this tree. Indicating that they are all const.
  virtual void getLeafs(ConstQueryTermList & tl) const = 0;
  /// Gives you all phrases of this tree.
  virtual void getPhrases(QueryNodeRefList & tl) = 0;
  /// Gives you all phrases of this tree. Indicating that they are all const.
  virtual void getPhrases(ConstQueryNodeRefList & tl) const = 0;
  virtual void setIndex(const vespalib::string & index) = 0;
  virtual const vespalib::string & getIndex() const = 0;

  /// Return the depth of this tree.
  virtual size_t depth() const { return 1; }
  /// Return the width of this tree.
  virtual size_t width() const { return 1; }
  static UP Build(const QueryNode * parent, const QueryNodeResultFactory & org, SimpleQueryStackDumpIterator & queryRep, bool allowRewrite);
};

/// A list conating the QuerNode objects. With copy/assignment.
typedef std::vector<QueryNode::UP> QueryNodeList;

}

