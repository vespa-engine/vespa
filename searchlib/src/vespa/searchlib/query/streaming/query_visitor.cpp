// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_visitor.h"
#include "query.h"
#include "near_query_node.h"
#include "onear_query_node.h"
#include "phrase_query_node.h"
#include "same_element_query_node.h"
#include "equiv_query_node.h"
#include "weak_and_query_node.h"
#include "queryterm.h"
#include "number_term.h"
#include "prefix_term.h"
#include "range_term.h"
#include "string_term.h"
#include "substring_term.h"
#include "suffix_term.h"
#include "location_term.h"
#include "predicate_query.h"
#include "regexp_term.h"
#include "fuzzy_term.h"
#include "nearest_neighbor_query_node.h"
#include "weighted_set_term.h"
#include "dot_product_term.h"
#include "wand_term.h"
#include "in_term.h"
#include "word_alternatives.h"

namespace search::streaming {

// Default implementations that do nothing
void QueryVisitorBase::handleNode(AndQueryNode &) {}
void QueryVisitorBase::handleNode(AndNotQueryNode &) {}
void QueryVisitorBase::handleNode(EquivQueryNode &) {}
void QueryVisitorBase::handleNode(NearQueryNode &) {}
void QueryVisitorBase::handleNode(ONearQueryNode &) {}
void QueryVisitorBase::handleNode(OrQueryNode &) {}
void QueryVisitorBase::handleNode(PhraseQueryNode &) {}
void QueryVisitorBase::handleNode(SameElementQueryNode &) {}
void QueryVisitorBase::handleNode(RankWithQueryNode &) {}
void QueryVisitorBase::handleNode(WeakAndQueryNode &) {}
void QueryVisitorBase::handleNode(FuzzyTerm &) {}
void QueryVisitorBase::handleNode(InTerm &) {}
void QueryVisitorBase::handleNode(LocationTerm &) {}
void QueryVisitorBase::handleNode(NearestNeighborQueryNode &) {}
void QueryVisitorBase::handleNode(NumberTerm &) {}
void QueryVisitorBase::handleNode(PredicateQuery &) {}
void QueryVisitorBase::handleNode(PrefixTerm &) {}
void QueryVisitorBase::handleNode(QueryTerm &) {}
void QueryVisitorBase::handleNode(RangeTerm &) {}
void QueryVisitorBase::handleNode(RegexpTerm &) {}
void QueryVisitorBase::handleNode(StringTerm &) {}
void QueryVisitorBase::handleNode(SubstringTerm &) {}
void QueryVisitorBase::handleNode(SuffixTerm &) {}
void QueryVisitorBase::handleNode(DotProductTerm &) {}
void QueryVisitorBase::handleNode(WandTerm &) {}
void QueryVisitorBase::handleNode(WeightedSetTerm &) {}
void QueryVisitorBase::handleNode(WordAlternatives &) {}
void QueryVisitorBase::handleNode(TrueNode &) {}
void QueryVisitorBase::handleNode(FalseNode &) {}

}
