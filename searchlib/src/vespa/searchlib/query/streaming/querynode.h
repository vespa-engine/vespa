// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
using QueryNodeRefList = std::vector<QueryNode *>;
/// Typedef a simple list that contains const references to QueryNodes.
using ConstQueryNodeRefList = std::vector<const QueryNode *>;
/// Typedef a simple list that contains references to QueryTerms.
using QueryTermList = std::vector<QueryTerm *>;
/// Typedef a simple list that contains const references to QueryTerms.
using ConstQueryTermList = std::vector<const QueryTerm *>;

/**
  This is the base of any node in the query tree. Both leaf nodes (terms)
  and operator nodes (AND, NOT, OR, PHRASE, NEAR, ONEAR, etc).
*/
class QueryNode
{
    static std::unique_ptr<QueryNode> build_nearest_neighbor_query_node(const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator& queryRep);
 public:
  using UP = std::unique_ptr<QueryNode>;

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
  static UP Build(const QueryNode * parent, const QueryNodeResultFactory& factory, SimpleQueryStackDumpIterator & queryRep, bool allowRewrite);
};

/// A list conating the QuerNode objects. With copy/assignment.
using QueryNodeList = std::vector<QueryNode::UP>;

}

