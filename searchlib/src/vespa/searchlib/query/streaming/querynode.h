// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "hit.h"
#include <vespa/searchcommon/common/element_ids.h>
#include <memory>

namespace search::fef {

class IIndexEnvironment;
class MatchData;

}

namespace search::streaming {

class QueryTerm;
class QueryNode;

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
public:
  virtual ~QueryNode() = default;
  /// This evalutes if the subtree starting here evaluates to true.
  virtual bool evaluate() = 0;
  /// This return the hitList for this subtree. Does only give meaning in a
  /// phrase search or any other search that requires position info.
  virtual const HitList & evaluateHits(HitList & hl);
  // Populate element_ids with the element ids matching the query for this subtree.
  virtual void get_element_ids(std::vector<uint32_t>& element_ids) = 0;
  virtual void unpack_match_data(uint32_t docid, fef::MatchData& match_data, const fef::IIndexEnvironment& index_env,
                                 search::common::ElementIds element_ids) = 0;
  /// Clears all the hitlists so the query tree can be reused.
  virtual void reset() = 0;
  /// Gives you all leafs of this tree.
  virtual void getLeaves(QueryTermList & tl) = 0;
  /// Gives you all leafs of this tree. Indicating that they are all const.
  virtual void getLeaves(ConstQueryTermList & tl) const = 0;
  virtual void setIndex(std::string index) = 0;
  virtual const std::string & getIndex() const = 0;

  /// Return the depth of this tree.
  virtual size_t depth() const { return 1; }
  /// Return the width of this tree.
  virtual size_t width() const { return 1; }
};

/// A list conating the QuerNode objects. With copy/assignment.
using QueryNodeList = std::vector<std::unique_ptr<QueryNode>>;

}

