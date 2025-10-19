// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "field_splitter.h"
#include "querynodes.h"
#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/querytreecreator.h>
#include <vespa/searchlib/queryeval/get_weight_from_node.h>
#include <vespa/vespalib/util/issue.h>
#include <cassert>
#include <map>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.field_splitter");

using search::query::Node;
using search::query::QueryBuilder;
using search::query::QueryTreeCreator;
using search::query::MultiTerm;
using search::query::TermVector;
using search::query::IntegerTermVector;
using search::query::StringTermVector;
using search::query::WeightedIntegerTermVector;
using search::query::WeightedStringTermVector;

namespace proton::matching {

namespace {

/**
 * ProtonTreeToString - Visitor that converts a Proton query tree to a human-readable string representation
 *
 * This class traverses a query tree and generates a formatted, indented string showing the structure
 * and details of the query. It's primarily used for debugging and logging purposes.
 *
 * Features:
 * - Hierarchical indentation to show query tree structure
 * - Detailed field information including field IDs, handles, and document frequency statistics
 * - Term details for simple terms (StringTerm, NumberTerm, etc.)
 * - Multi-term node details showing first few terms with weights
 * - Comprehensive ProtonTermData dumping for all node types
 *
 * Usage:
 *   ProtonTreeToString visitor;
 *   query_node->accept(visitor);
 *   std::string result = visitor.str();
 *
 * Or use the convenience function:
 *   std::string result = protonTreeToString(*query_node);
 */
class ProtonTreeToString : public search::query::CustomTypeVisitor<ProtonNodeTypes>
{
private:
    std::string _result;
    int _indent = 0;

    void addIndent() {
        for (int i = 0; i < _indent; ++i) {
            _result += "  ";
        }
    }

    void addLine(const std::string &line) {
        addIndent();
        _result += line;
        _result += "\n";
    }

    void visitChildren(const std::vector<Node *> &nodes) {
        _indent++;
        for (auto node : nodes) {
            node->accept(*this);
        }
        _indent--;
    }

    // Helper to dump ProtonTermData field information
    template <typename NodeType>
    std::string dumpProtonTermData(NodeType &node) {
        std::string result;
        if (node.numFields() > 0) {
            result += ", fields=[";
            for (size_t i = 0; i < node.numFields(); ++i) {
                if (i > 0) result += ", ";
                const auto &field_entry = node.field(i);
                result += field_entry.getName();
                result += "{id=" + std::to_string(field_entry.getFieldId());
                result += ", handle=" + std::to_string(field_entry.getHandle());
                result += ", attr=" + std::string(field_entry.attribute_field ? "true" : "false");
                result += ", docfreq=" + std::to_string(field_entry.get_matching_doc_count()) +
                         "/" + std::to_string(field_entry.get_total_doc_count());
                result += "}";
            }
            result += "]";
        }
        return result;
    }

    template <typename NodeType>
    void visitTerm(NodeType &node, const std::string &type) {
        std::string line = type + "(";
        if constexpr (requires { node.getTerm(); }) {
            line += "term=";
            if constexpr (std::is_same_v<decltype(node.getTerm()), const std::string&> ||
                          std::is_same_v<decltype(node.getTerm()), std::string>) {
                line += "'" + node.getTerm() + "'";
            } else {
                line += "[value]";
            }
            line += ", ";
        }
        line += "view='" + node.getView() + "'";
        line += dumpProtonTermData(node);
        line += ")";
        addLine(line);
    }

    template <typename NodeType>
    void visitMultiTerm(NodeType &node, const std::string &type) {
        std::string line = type + "(view='" + node.getView() + "', terms=" +
                          std::to_string(node.getNumTerms());
        line += dumpProtonTermData(node);
        line += ", first_terms=[";
        uint32_t max_show = std::min(node.getNumTerms(), 5u);
        for (uint32_t i = 0; i < max_show; ++i) {
            if (i > 0) line += ", ";
            auto type_enum = node.getType();
            if (type_enum == MultiTerm::Type::STRING || type_enum == MultiTerm::Type::WEIGHTED_STRING) {
                auto pair = node.getAsString(i);
                line += "'" + std::string(pair.first) + "'";
                if (type_enum == MultiTerm::Type::WEIGHTED_STRING) {
                    line += ":" + std::to_string(pair.second.percent());
                }
            } else if (type_enum == MultiTerm::Type::INTEGER || type_enum == MultiTerm::Type::WEIGHTED_INTEGER) {
                auto pair = node.getAsInteger(i);
                line += std::to_string(pair.first);
                if (type_enum == MultiTerm::Type::WEIGHTED_INTEGER) {
                    line += ":" + std::to_string(pair.second.percent());
                }
            }
        }
        if (node.getNumTerms() > max_show) {
            line += ", ...";
        }
        line += "])";
        addLine(line);
    }

public:
    std::string str() const { return _result; }

    // Intermediate nodes
    void visit(ProtonAnd &node) override {
        addLine("AND");
        visitChildren(node.getChildren());
    }

    void visit(ProtonAndNot &node) override {
        addLine("ANDNOT");
        visitChildren(node.getChildren());
    }

    void visit(ProtonOr &node) override {
        addLine("OR");
        visitChildren(node.getChildren());
    }

    void visit(ProtonRank &node) override {
        addLine("RANK");
        visitChildren(node.getChildren());
    }

    void visit(ProtonWeakAnd &node) override {
        addLine("WEAKAND(targetHits=" + std::to_string(node.getTargetNumHits()) +
                ", view='" + node.getView() + "')");
        visitChildren(node.getChildren());
    }

    void visit(ProtonNear &node) override {
        addLine("NEAR(distance=" + std::to_string(node.getDistance()) + ")");
        visitChildren(node.getChildren());
    }

    void visit(ProtonONear &node) override {
        addLine("ONEAR(distance=" + std::to_string(node.getDistance()) + ")");
        visitChildren(node.getChildren());
    }

    void visit(ProtonEquiv &node) override {
        std::string line = "EQUIV(id=" + std::to_string(node.getId()) +
                          ", weight=" + std::to_string(node.getWeight().percent());
        line += dumpProtonTermData(node);
        line += ")";
        addLine(line);
        visitChildren(node.getChildren());
    }

    void visit(ProtonPhrase &node) override {
        std::string line = "PHRASE(view='" + node.getView() + "'";
        line += dumpProtonTermData(node);
        line += ")";
        addLine(line);
        visitChildren(node.getChildren());
    }

    void visit(ProtonSameElement &node) override {
        addLine("SAMEELEMENT(view='" + node.getView() + "')");
        visitChildren(node.getChildren());
    }

    // Term nodes
    void visit(ProtonNumberTerm &node) override {
        visitTerm(node, "NumberTerm");
    }

    void visit(ProtonStringTerm &node) override {
        visitTerm(node, "StringTerm");
    }

    void visit(ProtonPrefixTerm &node) override {
        visitTerm(node, "PrefixTerm");
    }

    void visit(ProtonSubstringTerm &node) override {
        visitTerm(node, "SubstringTerm");
    }

    void visit(ProtonSuffixTerm &node) override {
        visitTerm(node, "SuffixTerm");
    }

    void visit(ProtonRangeTerm &node) override {
        visitTerm(node, "RangeTerm");
    }

    void visit(ProtonLocationTerm &node) override {
        visitTerm(node, "LocationTerm");
    }

    void visit(ProtonRegExpTerm &node) override {
        visitTerm(node, "RegExpTerm");
    }

    void visit(ProtonFuzzyTerm &node) override {
        visitTerm(node, "FuzzyTerm");
    }

    // Multi-term nodes
    void visit(ProtonWeightedSetTerm &node) override {
        visitMultiTerm(node, "WeightedSetTerm");
    }

    void visit(ProtonDotProduct &node) override {
        visitMultiTerm(node, "DotProduct");
    }

    void visit(ProtonWandTerm &node) override {
        std::string line = "WandTerm(view='" + node.getView() + "', terms=" +
                          std::to_string(node.getNumTerms()) +
                          ", targetHits=" + std::to_string(node.getTargetNumHits());
        line += dumpProtonTermData(node);
        line += ")";
        addLine(line);
    }

    void visit(ProtonInTerm &node) override {
        visitMultiTerm(node, "InTerm");
    }

    void visit(ProtonWordAlternatives &node) override {
        visitMultiTerm(node, "WordAlternatives");
        if (!node.children.empty()) {
            _indent++;
            for (auto& child : node.children) {
                child->accept(*this);
            }
            _indent--;
        }
    }

    void visit(ProtonPredicateQuery &node) override {
        addLine("PredicateQuery(view='" + node.getView() + "')");
    }

    void visit(ProtonNearestNeighborTerm &node) override {
        addLine("NearestNeighborTerm(tensor='" + node.get_query_tensor_name() +
                "', view='" + node.getView() + "')");
    }

    void visit(ProtonTrue &) override {
        addLine("TRUE");
    }

    void visit(ProtonFalse &) override {
        addLine("FALSE");
    }
};

std::string protonTreeToString(Node &root) {
    ProtonTreeToString visitor;
    root.accept(visitor);
    return visitor.str();
}

/**
 * FieldSplitterVisitor - Visitor that splits multi-field query terms into separate per-field terms
 *
 * This visitor transforms a query tree where terms can have multiple fields into a normalized form
 * where each term operates on a single field. When a term has multiple fields, it's split into
 * separate term instances connected with OR nodes.
 *
 * Purpose:
 * Query terms in Vespa can reference multiple fields (e.g., search in both "title" and "body").
 * This visitor splits such terms into field-specific variants to simplify query execution.
 *
 * Transformation examples:
 *
 * 1. Simple term with two fields:
 *    StringTerm("foo", fields=[title, body])
 *    =>
 *    OR(StringTerm("foo", field=title), StringTerm("foo", field=body))
 *
 * 2. Phrase with multiple fields:
 *    Phrase(fields=[title, body], children=[term1, term2])
 *    =>
 *    OR(Phrase(field=title, children=[term1_title, term2_title]),
 *       Phrase(field=body, children=[term1_body, term2_body]))
 *
 * 3. Equiv with children having different fields:
 *    Equiv(child1[fields=title,body], child2[fields=body,author])
 *    =>
 *    OR(Equiv(child1_title),
 *       Equiv(child1_body, child2_body),
 *       Equiv(child2_author))
 *
 * Special handling:
 * - Phrase nodes: Children are forced to use the same field as the phrase
 * - Equiv nodes: Gathers children by field, creating one Equiv per field
 * - Multi-term nodes: WeightedSet, DotProduct, WandTerm, InTerm, WordAlternatives
 * - Forced field mode: When inside a Phrase, children must use the phrase's field
 *
 * State:
 * - _builder: QueryBuilder for constructing the transformed tree
 * - _force_field_id: When set, forces children to use this specific field (for Phrase)
 * - _has_error: Set when field splitting fails (e.g., forced field not found)
 *
 * Usage:
 *   FieldSplitterVisitor visitor;
 *   query_node->accept(visitor);
 *   Node::UP result = visitor.build();
 */
class FieldSplitterVisitor : public search::query::CustomTypeVisitor<ProtonNodeTypes>
{
private:
    QueryBuilder<ProtonNodeTypes> _builder;
    uint32_t _force_field_id = search::fef::IllegalFieldId;
    bool _has_error = false;

    void visitNodes(const std::vector<Node *> &nodes) {
        for (auto node : nodes) {
            node->accept(*this);
        }
    }

    // Helper to split and visit children with a forced field
    void splitAndVisitChildrenForField(const std::vector<Node *> &nodes, uint32_t field_id) {
        // Save and restore forced field id
        uint32_t saved_field_id = _force_field_id;
        _force_field_id = field_id;
        for (Node *child : nodes) {
            child->accept(*this);
        }
        _force_field_id = saved_field_id;
    }

    // Helper to get field IDs from a ProtonTermData node
    std::set<uint32_t> getFieldIds(const ProtonTermData &term_data) const {
        std::set<uint32_t> fields;
        for (size_t i = 0; i < term_data.numFields(); ++i) {
            fields.insert(term_data.field(i).getFieldId());
        }
        return fields;
    }

    // Helper to check if all children have the same field set
    bool allChildrenHaveSameFields(const std::vector<Node *> &children, const std::set<uint32_t> &expected_fields) const {
        for (Node *child : children) {
            auto* term_data = dynamic_cast<ProtonTermData*>(child);
            if (!term_data || term_data->numFields() == 0) {
                return false;
            }

            std::set<uint32_t> child_fields = getFieldIds(*term_data);
            if (child_fields != expected_fields) {
                return false;
            }
        }
        return true;
    }

    // Helper to create a non-split SameElement (pass-through)
    void handleWithoutSplit(ProtonSameElement &node) {
        _builder.addSameElement(node.getChildren().size(), node.getView(),
                               node.getId(), node.getWeight()).set_expensive(node.is_expensive());
        visitNodes(node.getChildren());
    }

    // Helper to split SameElement across multiple fields
    void splitSameElementByFields(ProtonSameElement &node, const std::set<uint32_t> &fields) {
        _builder.addOr(fields.size());

        for (uint32_t field_id : fields) {
            auto &replica = _builder.addSameElement(node.getChildren().size(), node.getView(),
                                                   node.getId(), node.getWeight());
            replica.set_expensive(node.is_expensive());
            splitAndVisitChildrenForField(node.getChildren(), field_id);
        }
    }

    void copyState(const search::query::Term &original, search::query::Term &replica) {
        replica.setRanked(original.isRanked());
        replica.setPositionData(original.usePositionData());
        replica.set_prefix_match(original.prefix_match());
    }

    // Helper to copy ProtonTermData state for a specific field
    template <typename TermType>
    void copyProtonTermDataForField(TermType &original, TermType &replica, size_t field_idx) {
        // Copy the specific field entry from original to replica
        if (field_idx < original.numFields()) {
            const auto &field_entry = original.field(field_idx);
            replica.copyFieldEntry(field_entry);
        }
    }

    // Helper to replicate subterms for multi-term nodes
    std::unique_ptr<TermVector> replicate_subterms(const MultiTerm& original) {
        uint32_t num_terms = original.getNumTerms();
        switch (original.getType()) {
        case MultiTerm::Type::STRING: {
            auto replica = std::make_unique<StringTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                auto v = original.getAsString(i);
                replica->addTerm(v.first);
            }
            return replica;
        }
        case MultiTerm::Type::WEIGHTED_STRING: {
            auto replica = std::make_unique<WeightedStringTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                auto v = original.getAsString(i);
                replica->addTerm(v.first, v.second);
            }
            return replica;
        }
        case MultiTerm::Type::INTEGER: {
            auto replica = std::make_unique<IntegerTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                auto v = original.getAsInteger(i);
                replica->addTerm(v.first);
            }
            return replica;
        }
        case MultiTerm::Type::WEIGHTED_INTEGER: {
            auto replica = std::make_unique<WeightedIntegerTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                auto v = original.getAsInteger(i);
                replica->addTerm(v.first, v.second);
            }
            return replica;
        }
        case MultiTerm::Type::UNKNOWN:
            assert(num_terms == 0);
        }
        return std::make_unique<WeightedStringTermVector>(num_terms);
    }

    // Helper to get node type name for error reporting
    template <typename NodeType>
    const char* getNodeTypeName() {
        if constexpr (std::is_same_v<NodeType, ProtonStringTerm>) return "StringTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonNumberTerm>) return "NumberTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonPrefixTerm>) return "PrefixTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonSubstringTerm>) return "SubstringTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonSuffixTerm>) return "SuffixTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonRangeTerm>) return "RangeTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonLocationTerm>) return "LocationTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonRegExpTerm>) return "RegExpTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonFuzzyTerm>) return "FuzzyTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonPhrase>) return "Phrase";
        else if constexpr (std::is_same_v<NodeType, ProtonWeightedSetTerm>) return "WeightedSetTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonDotProduct>) return "DotProduct";
        else if constexpr (std::is_same_v<NodeType, ProtonWandTerm>) return "WandTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonInTerm>) return "InTerm";
        else if constexpr (std::is_same_v<NodeType, ProtonWordAlternatives>) return "WordAlternatives";
        else return "UnknownNode";
    }

    // Helper to replicate a term for a specific field
    template <typename NodeType>
    void replicateTermForField(NodeType &node, size_t field_idx);

    template <typename NodeType>
    void splitTerm(NodeType &node) {
        // If we're forced to use a specific field (inside a phrase), use only that field
        if (_force_field_id != search::fef::IllegalFieldId) {
            // Find the matching field index
            for (size_t i = 0; i < node.numFields(); ++i) {
                if (node.field(i).getFieldId() == _force_field_id) {
                    replicateTermForField(node, i);
                    return;
                }
            }
            // Field not found - report error and set error flag
            vespalib::Issue::report("field splitting for %s failed: forced field_id %u not found in node's %zu fields",
                                   getNodeTypeName<NodeType>(), _force_field_id, node.numFields());
            _has_error = true;
            return;
        }

        // Normal case: split across all fields
        size_t num_fields = node.numFields();

        if (num_fields <= 1) {
            // No splitting needed - just replicate the node as-is
            replicateTermForField(node, 0);
        } else {
            // Multiple fields - create OR with one term per field
            _builder.addOr(num_fields);
            for (size_t i = 0; i < num_fields; ++i) {
                replicateTermForField(node, i);
            }
        }
    }

public:
    Node::UP build() {
        if (_has_error) {
            return Node::UP();
        }
        return _builder.build();
    }

    // Intermediate nodes - recurse on children
    void visit(ProtonAnd &node) override {
        _builder.addAnd(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void visit(ProtonAndNot &node) override {
        _builder.addAndNot(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void visit(ProtonOr &node) override {
        _builder.addOr(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void visit(ProtonRank &node) override {
        _builder.addRank(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void visit(ProtonWeakAnd &node) override {
        _builder.addWeakAnd(node.getChildren().size(), node.getTargetNumHits(), node.getView());
        visitNodes(node.getChildren());
    }

    void visit(ProtonNear &node) override {
        _builder.addNear(node.getChildren().size(), node.getDistance(),
                        node.num_negative_terms(), node.exclusion_distance());
        visitNodes(node.getChildren());
    }

    void visit(ProtonONear &node) override {
        _builder.addONear(node.getChildren().size(), node.getDistance(),
                         node.num_negative_terms(), node.exclusion_distance());
        visitNodes(node.getChildren());
    }

    void visit(ProtonEquiv &node) override {
        // Approach: Determine which fields are present in each child,
        // then create one Equiv per field containing only children that have that field

        // Build map: field_id -> list of original child indices that have this field
        std::map<uint32_t, std::vector<size_t>> field_to_children;
        const auto& children = node.getChildren();

        for (size_t child_idx = 0; child_idx < children.size(); ++child_idx) {
            Node *child = children[child_idx];
            if (auto* term_data = dynamic_cast<ProtonTermData*>(child)) {
                for (size_t i = 0; i < term_data->numFields(); ++i) {
                    uint32_t field_id = term_data->field(i).getFieldId();
                    field_to_children[field_id].push_back(child_idx);
                }
            }
        }

        if (field_to_children.empty()) {
            vespalib::Issue::report("field splitting for Equiv node failed: no fields found in any children (id=%d, weight=%d, num_children=%zu)",
                                   node.getId(), node.getWeight().percent(), children.size());
            _has_error = true;
            return;
        }

        if (field_to_children.size() == 1) {
            // Only one field - create single Equiv with all children
            uint32_t field_id = field_to_children.begin()->first;
            const auto& child_indices = field_to_children.begin()->second;

            auto &replica = _builder.addEquiv(child_indices.size(), node.getId(), node.getWeight());

            // Visit each child with forced field
            uint32_t saved_field_id = _force_field_id;
            _force_field_id = field_id;
            for (size_t idx : child_indices) {
                children[idx]->accept(*this);
            }
            _force_field_id = saved_field_id;

            // Resolve field metadata from children
            replica.resolveFromChildren(replica.getChildren());
        } else {
            // Multiple fields - create OR with one Equiv per field
            _builder.addOr(field_to_children.size());

            for (auto& [field_id, child_indices] : field_to_children) {
                // Create Equiv with only the children that have this field
                auto &replica = _builder.addEquiv(child_indices.size(), node.getId(), node.getWeight());

                // Visit each child with forced field
                uint32_t saved_field_id = _force_field_id;
                _force_field_id = field_id;
                for (size_t idx : child_indices) {
                    children[idx]->accept(*this);
                }
                _force_field_id = saved_field_id;

                // Resolve field metadata from children
                replica.resolveFromChildren(replica.getChildren());
            }
        }
    }

    void visit(ProtonPhrase &node) override {
        splitTerm(node);
    }

    void visit(ProtonSameElement &node) override {
        // Check if we can split this SameElement by fields
        // SameElement is-a ProtonTermData, so check its fields first
        // We can split if:
        // 1. SameElement has multiple fields
        // 2. All children have the same set of fields

        // Check SameElement's own fields first
        if (node.numFields() <= 1) {
            // No splitting needed - single field or no fields
            LOG(debug, "SameElement not split: has %zu field(s)", node.numFields());
            handleWithoutSplit(node);
            return;
        }

        // Get the field set from SameElement
        std::set<uint32_t> same_element_fields = getFieldIds(node);

        // Check if all children have the same field set as SameElement
        if (!allChildrenHaveSameFields(node.getChildren(), same_element_fields)) {
            // Children have different fields or lack field info - can't split
            LOG(debug, "SameElement not split: children have different fields or lack field info");
            handleWithoutSplit(node);
            return;
        }

        // All children have the same multiple fields as SameElement - split like Phrase
        LOG(debug, "Splitting SameElement across %zu fields", same_element_fields.size());
        splitSameElementByFields(node, same_element_fields);
    }

    // Terms that need splitting
    void visit(ProtonNumberTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonStringTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonPrefixTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonSubstringTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonSuffixTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonRangeTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonLocationTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonRegExpTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonFuzzyTerm &node) override {
        splitTerm(node);
    }

    // Multi-terms - split by field if needed
    void visit(ProtonWeightedSetTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonDotProduct &node) override {
        splitTerm(node);
    }

    void visit(ProtonWandTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonInTerm &node) override {
        splitTerm(node);
    }

    void visit(ProtonWordAlternatives &node) override {
        splitTerm(node);
    }

    void visit(ProtonPredicateQuery &node) override {
        copyState(node, _builder.addPredicateQuery(
            std::make_unique<search::query::PredicateQueryTerm>(*node.getTerm()),
            node.getView(), node.getId(), node.getWeight()));
    }

    void visit(ProtonNearestNeighborTerm &node) override {
        copyState(node, _builder.add_nearest_neighbor_term(
            node.get_query_tensor_name(), node.getView(),
            node.getId(), node.getWeight(), node.get_target_num_hits(),
            node.get_allow_approximate(), node.get_explore_additional_hits(),
            node.get_distance_threshold()));
    }

    void visit(ProtonTrue &) override {
        _builder.add_true_node();
    }

    void visit(ProtonFalse &) override {
        _builder.add_false_node();
    }
};

// Template specializations for replicateTermForField
template <>
void FieldSplitterVisitor::replicateTermForField<ProtonNumberTerm>(ProtonNumberTerm &node, size_t field_idx) {
    auto &replica = _builder.addNumberTerm(
        node.getTerm(), node.field(field_idx).getName(),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonStringTerm>(ProtonStringTerm &node, size_t field_idx) {
    auto &replica = _builder.addStringTerm(
        node.getTerm(), node.field(field_idx).getName(),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonPrefixTerm>(ProtonPrefixTerm &node, size_t field_idx) {
    auto &replica = _builder.addPrefixTerm(
        node.getTerm(), node.field(field_idx).getName(),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonSubstringTerm>(ProtonSubstringTerm &node, size_t field_idx) {
    auto &replica = _builder.addSubstringTerm(
        node.getTerm(), node.field(field_idx).getName(),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonSuffixTerm>(ProtonSuffixTerm &node, size_t field_idx) {
    auto &replica = _builder.addSuffixTerm(
        node.getTerm(), node.field(field_idx).getName(),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonRangeTerm>(ProtonRangeTerm &node, size_t field_idx) {
    auto &replica = _builder.addRangeTerm(
        node.getTerm(), node.field(field_idx).getName(),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonLocationTerm>(ProtonLocationTerm &node, size_t field_idx) {
    auto &replica = _builder.addLocationTerm(
        node.getTerm(), node.field(field_idx).getName(),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonRegExpTerm>(ProtonRegExpTerm &node, size_t field_idx) {
    auto &replica = _builder.addRegExpTerm(
        node.getTerm(), node.field(field_idx).getName(),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonFuzzyTerm>(ProtonFuzzyTerm &node, size_t field_idx) {
    auto &replica = _builder.addFuzzyTerm(
        node.getTerm(), node.field(field_idx).getName(),
        node.getId(), node.getWeight(),
        node.max_edit_distance(), node.prefix_lock_length(), node.prefix_match());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonPhrase>(ProtonPhrase &node, size_t field_idx) {
    const std::string &field_name = node.field(field_idx).getName();
    uint32_t field_id = node.field(field_idx).getFieldId();

    auto &replica = _builder.addPhrase(node.getChildren().size(), field_name,
                                      node.getId(), node.getWeight());
    replica.set_expensive(node.is_expensive());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);

    // Process children with the forced field - they will only use this field
    splitAndVisitChildrenForField(node.getChildren(), field_id);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonWordAlternatives>(ProtonWordAlternatives &node, size_t field_idx) {
    const std::string &field_name = node.field(field_idx).getName();

    // Replicate the term vector - WordAlternatives uses subterms like other multi-terms
    auto &replica = _builder.add_word_alternatives(
        replicate_subterms(node), field_name, node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);

    // If there are children, copy the field entry to them
    if (!replica.children.empty()) {
        const auto &field_entry = node.field(field_idx);
        for (auto& child : replica.children) {
            child->copyFieldEntry(field_entry);
        }
    }
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonWeightedSetTerm>(ProtonWeightedSetTerm &node, size_t field_idx) {
    const std::string &field_name = node.field(field_idx).getName();

    auto &replica = _builder.addWeightedSetTerm(
        replicate_subterms(node), node.getType(), field_name, node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonDotProduct>(ProtonDotProduct &node, size_t field_idx) {
    const std::string &field_name = node.field(field_idx).getName();

    auto &replica = _builder.addDotProduct(
        replicate_subterms(node), node.getType(), field_name, node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonWandTerm>(ProtonWandTerm &node, size_t field_idx) {
    const std::string &field_name = node.field(field_idx).getName();

    auto &replica = _builder.addWandTerm(
        replicate_subterms(node), node.getType(), field_name, node.getId(), node.getWeight(),
        node.getTargetNumHits(), node.getScoreThreshold(), node.getThresholdBoostFactor());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonInTerm>(ProtonInTerm &node, size_t field_idx) {
    const std::string &field_name = node.field(field_idx).getName();

    auto &replica = _builder.add_in_term(
        replicate_subterms(node), node.getType(), field_name, node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

}

Node::UP FieldSplitter::split_terms(Node::UP root) {
    LOG(debug, "field splitting input tree:\n%s", protonTreeToString(*root).c_str());
    FieldSplitterVisitor visitor;
    root->accept(visitor);
    Node::UP result = visitor.build();
    if (!result) {
        // Error during splitting, return original tree
        LOG(info, "field splitting failed, returning original tree");
        return root;
    }
    LOG(info, "field splitting completed, result tree:\n%s", protonTreeToString(*result).c_str());
    return result;
}

}
