// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace search::streaming {

// Forward declarations
class AndQueryNode;
class AndNotQueryNode;
class EquivQueryNode;
class FalseNode;
class FuzzyTerm;
class InTerm;
class LocationTerm;
class NearQueryNode;
class NearestNeighborQueryNode;
class NumberTerm;
class ONearQueryNode;
class OrQueryNode;
class PhraseQueryNode;
class PredicateQuery;
class PrefixTerm;
class RangeTerm;
class RankWithQueryNode;
class RegexpTerm;
class SameElementQueryNode;
class StringTerm;
class SubstringTerm;
class SuffixTerm;
class TrueNode;
class WeakAndQueryNode;
class WeightedSetTerm;
class DotProductTerm;
class WandTerm;
class WordAlternatives;

/**
 * Traits class that maps the search::query node types to their
 * corresponding search::streaming implementations.
 *
 * This provides a convenient way to reference all the streaming node types.
 *
 * For visiting streaming query nodes, you have several options:
 *
 * 1. Use streaming::QueryVisitor for basic visiting:
 *
 *   class MyStreamingVisitor : public streaming::QueryVisitor {
 *       void visit(streaming::StringTerm& term) override { ... }
 *       ...
 *   };
 *
 * 2. Use streaming::TermVisitor when you only want to visit term nodes
 *    (connector nodes are automatically traversed):
 *
 *   class MyTermVisitor : public streaming::TermVisitor {
 *       void visit(streaming::StringTerm& term) override { ... }
 *       void visit(streaming::PrefixTerm& term) override { ... }
 *       // ... implement other term types
 *   };
 *
 * 3. Use streaming::TemplateTermVisitor when you want to handle all terms
 *    the same way:
 *
 *   class MyCollector : public streaming::TemplateTermVisitor<MyCollector> {
 *       template <class TermType>
 *       void visitTerm(TermType& term) { ... }
 *   };
 *
 * See streaming/query_visitor.h, streaming/term_visitor.h, and streaming/template_term_visitor.h
 */
struct NodeTypes {
    using And = AndQueryNode;
    using AndNot = AndNotQueryNode;
    using Equiv = EquivQueryNode;
    using NumberTerm = streaming::NumberTerm;
    using LocationTerm = streaming::LocationTerm;
    using Near = NearQueryNode;
    using ONear = ONearQueryNode;
    using Or = OrQueryNode;
    using Phrase = PhraseQueryNode;
    using SameElement = SameElementQueryNode;
    using PrefixTerm = streaming::PrefixTerm;
    using RangeTerm = streaming::RangeTerm;
    using Rank = RankWithQueryNode;
    using StringTerm = streaming::StringTerm;
    using SubstringTerm = streaming::SubstringTerm;
    using SuffixTerm = streaming::SuffixTerm;
    using WeakAnd = WeakAndQueryNode;
    using WeightedSetTerm = streaming::WeightedSetTerm;
    using DotProduct = DotProductTerm;
    using WandTerm = streaming::WandTerm;
    using PredicateQuery = streaming::PredicateQuery;
    using RegExpTerm = RegexpTerm;
    using NearestNeighborTerm = NearestNeighborQueryNode;
    using TrueQueryNode = TrueNode;
    using FalseQueryNode = FalseNode;
    using FuzzyTerm = streaming::FuzzyTerm;
    using InTerm = streaming::InTerm;
    using WordAlternatives = streaming::WordAlternatives;
};

}
