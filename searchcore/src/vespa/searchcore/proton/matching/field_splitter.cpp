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
        std::string line = "SAMEELEMENT(view='" + node.getView() + "'";
        line += dumpProtonTermData(node);
        line += ")";
        addLine(line);
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
        std::string line = "WordAlternatives(view='" + node.getView() + "', terms=" +
                          std::to_string(node.getNumTerms());
        line += dumpProtonTermData(node);
        line += ", children=[";
        const auto& children = node.getChildren();
        uint32_t max_show = std::min(static_cast<uint32_t>(children.size()), 5u);
        for (uint32_t i = 0; i < max_show; ++i) {
            if (i > 0) line += ", ";
            line += "'" + children[i]->getTerm() + "'";
        }
        if (children.size() > max_show) {
            line += ", ...";
        }
        line += "])";
        addLine(line);

        // Visit children if present
        if (!children.empty()) {
            _indent++;
            for (const auto& child : children) {
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

using ProtonBuilder = QueryBuilder<ProtonNodeTypes>;

template<typename T>
T& genericAddSimpleTerm(ProtonBuilder& builder,
                        const std::string & term,
                        const std::string & view,
                        int32_t id,
                        search::query::Weight weight);

template<>
ProtonNodeTypes::StringTerm&
genericAddSimpleTerm<ProtonNodeTypes::StringTerm>(ProtonBuilder& builder,
                                                  const std::string & term,
                                                  const std::string & view,
                                                  int32_t id,
                                                  search::query::Weight weight)
{
    return builder.addStringTerm(term, view, id, weight);
}

template<>
ProtonNodeTypes::NumberTerm&
genericAddSimpleTerm<ProtonNodeTypes::NumberTerm>(ProtonBuilder& builder,
                                                  const std::string & term,
                                                  const std::string & view,
                                                  int32_t id,
                                                  search::query::Weight weight)
{
    return builder.addNumberTerm(term, view, id, weight);
}

template<>
ProtonNodeTypes::PrefixTerm&
genericAddSimpleTerm<ProtonNodeTypes::PrefixTerm>(ProtonBuilder& builder,
                                                  const std::string & term,
                                                  const std::string & view,
                                                  int32_t id,
                                                  search::query::Weight weight)
{
    return builder.addPrefixTerm(term, view, id, weight);
}

template<>
ProtonNodeTypes::SubstringTerm&
genericAddSimpleTerm<ProtonNodeTypes::SubstringTerm>(ProtonBuilder& builder,
                                                     const std::string & term,
                                                     const std::string & view,
                                                     int32_t id,
                                                     search::query::Weight weight)
{
    return builder.addSubstringTerm(term, view, id, weight);
}

template<>
ProtonNodeTypes::SuffixTerm&
genericAddSimpleTerm<ProtonNodeTypes::SuffixTerm>(ProtonBuilder& builder,
                                                  const std::string & term,
                                                  const std::string & view,
                                                  int32_t id,
                                                  search::query::Weight weight)
{
    return builder.addSuffixTerm(term, view, id, weight);
}

template<>
ProtonNodeTypes::RegExpTerm&
genericAddSimpleTerm<ProtonNodeTypes::RegExpTerm>(ProtonBuilder& builder,
                                                  const std::string & term,
                                                  const std::string & view,
                                                  int32_t id,
                                                  search::query::Weight weight)
{
    return builder.addRegExpTerm(term, view, id, weight);
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
 * - Phrase nodes: Children won't have fields, so they are visited as-is
 * - SameElement nodes: Children are forced to use the same field as the SameElement
 * - Equiv nodes: Gathers children by field, creating one Equiv per field
 * - Multi-term nodes: WeightedSet, DotProduct, WandTerm, InTerm, WordAlternatives
 * - Forced field mode: When inside a SameElement, children must use the SameElement's field
 *
 * State:
 * - _builder: QueryBuilder for constructing the transformed tree
 * - _force_field_id: When set, forces children to use this specific field (for SameElement)
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
    ProtonBuilder _builder;
    uint32_t _force_field_id = search::fef::IllegalFieldId;
    bool _has_error = false;

    // ===== Node Traversal Helpers =====

    void visitNodes(const std::vector<Node *> &nodes) {
        for (auto node : nodes) {
            node->accept(*this);
        }
    }

    // Helper to visit children with a forced field (used for SameElement)
    // Sets _force_field_id temporarily so that child terms only use the specified field
    void splitAndVisitChildrenForField(const std::vector<Node *> &nodes, uint32_t field_id) {
        uint32_t saved_field_id = _force_field_id;
        _force_field_id = field_id;
        visitNodes(nodes);
        _force_field_id = saved_field_id;
    }

    // ===== Field Analysis Helpers =====

    // Helper to get field IDs from a ProtonTermData node
    static std::set<uint32_t> getFieldIds(const ProtonTermData &term_data) {
        std::set<uint32_t> fields;
        for (size_t i = 0; i < term_data.numFields(); ++i) {
            fields.insert(term_data.field(i).getFieldId());
        }
        return fields;
    }

    // Helper to check if all children have the same field set
    static bool allChildrenHaveSameFields(const std::vector<Node *> &children,
                                          const std::set<uint32_t> &expected_fields)
    {
        for (Node *child : children) {
            auto* term_data = dynamic_cast<ProtonTermData*>(child);
            if (!term_data || term_data->numFields() != expected_fields.size()) {
                return false;
            }
            if (getFieldIds(*term_data) != expected_fields) {
                return false;
            }
        }
        return true;
    }

    // ===== Node-Specific Helpers =====

    // Helper to create SameElement replica with common properties
    ProtonSameElement& createSameElementReplica(ProtonSameElement &node) {
        auto &replica = _builder.addSameElement(node.getChildren().size(), node.getView(),
                                               node.getId(), node.getWeight());
        replica.set_expensive(node.is_expensive());
        return replica;
    }

    // Helper to create a non-split SameElement (pass-through)
    void handleWithoutSplit(ProtonSameElement &node) {
        auto &replica = createSameElementReplica(node);
        // Copy ProtonTermData state - should have exactly one field when not splitting
        if (node.numFields() == 1) {
            copyProtonTermDataForField(node, replica, 0);
        }
        visitNodes(node.getChildren());
    }

    // Helper to split SameElement across multiple fields
    // Children are forced to use each specific field via splitAndVisitChildrenForField
    void splitSameElementByFields(ProtonSameElement &node, const std::set<uint32_t> &fields) {
        _builder.addOr(fields.size());
        for (uint32_t field_id : fields) {
            createSameElementReplica(node);
            splitAndVisitChildrenForField(node.getChildren(), field_id);
        }
    }

    // ===== Term State Copying Helpers =====

    static void copyState(const search::query::Term &original, search::query::Term &replica) {
        replica.setRanked(original.isRanked());
        replica.setPositionData(original.usePositionData());
        replica.set_prefix_match(original.prefix_match());
    }

    // Helper to copy ProtonTermData state for a specific field
    template <typename TermType>
    static void copyProtonTermDataForField(TermType &original, TermType &replica, size_t field_idx) {
        // Copy the specific field entry from original to replica
        if (field_idx < original.numFields()) {
            replica.useFieldEntry(original.field(field_idx));
        }
    }

    // Helper to replicate subterms for multi-term nodes
    static std::unique_ptr<TermVector> replicate_subterms(const MultiTerm& original) {
        uint32_t num_terms = original.getNumTerms();
        switch (original.getType()) {
        case MultiTerm::Type::STRING: {
            auto replica = std::make_unique<StringTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                replica->addTerm(original.getAsString(i).first);
            }
            return replica;
        }
        case MultiTerm::Type::WEIGHTED_STRING: {
            auto replica = std::make_unique<WeightedStringTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                auto [term, weight] = original.getAsString(i);
                replica->addTerm(term, weight);
            }
            return replica;
        }
        case MultiTerm::Type::INTEGER: {
            auto replica = std::make_unique<IntegerTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                replica->addTerm(original.getAsInteger(i).first);
            }
            return replica;
        }
        case MultiTerm::Type::WEIGHTED_INTEGER: {
            auto replica = std::make_unique<WeightedIntegerTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                auto [value, weight] = original.getAsInteger(i);
                replica->addTerm(value, weight);
            }
            return replica;
        }
        case MultiTerm::Type::UNKNOWN:
            assert(num_terms == 0);
        }
        return std::make_unique<WeightedStringTermVector>(num_terms);
    }

    // ===== Error Handling Helpers =====

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

    // ===== Term Splitting Logic =====

    // Helper to get the view/field name for a term being replicated
    // Uses the field name from field_idx if available, otherwise falls back to the node's view
    template <typename NodeType>
    const std::string& getFieldNameOrView(NodeType &node, size_t field_idx) const {
        if (field_idx < node.numFields()) {
            return node.field(field_idx).getName();
        }
        return node.getView();
    }

    // Helper to replicate a term for a specific field
    template <typename NodeType>
    void replicateTermForField(NodeType &node, size_t field_idx) {
        // Default implementation for simple term types that work with genericAddSimpleTerm
        auto &replica = genericAddSimpleTerm<NodeType>(_builder,
            node.getTerm(), getFieldNameOrView(node, field_idx),
            node.getId(), node.getWeight());
        copyState(node, replica);
        copyProtonTermDataForField(node, replica, field_idx);
    }

    template <typename NodeType>
    void splitTerm(NodeType &node) {
        // If we're forced to use a specific field (inside SameElement), use only that field
        if (_force_field_id != search::fef::IllegalFieldId) {
            // Find the matching field index
            for (size_t i = 0; i < node.numFields(); ++i) {
                if (node.field(i).getFieldId() == _force_field_id) {
                    replicateTermForField(node, i);
                    return;
                }
            }
            // Field not found - report error and set error flag
            LOG(debug, "field splitting for %s failed: forced field_id %u not found in node's %zu fields",
                getNodeTypeName<NodeType>(), _force_field_id, node.numFields());
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

    // Helper to build field-to-children mapping for Equiv nodes
    std::map<uint32_t, std::vector<size_t>> buildFieldToChildrenMap(const std::vector<Node *> &children) {
        std::map<uint32_t, std::vector<size_t>> field_to_children;
        for (size_t child_idx = 0; child_idx < children.size(); ++child_idx) {
            if (auto* term_data = dynamic_cast<ProtonTermData*>(children[child_idx])) {
                for (size_t i = 0; i < term_data->numFields(); ++i) {
                    field_to_children[term_data->field(i).getFieldId()].push_back(child_idx);
                }
            }
        }
        return field_to_children;
    }

    // Helper to create and populate an Equiv node for a specific field
    void createEquivForField(ProtonEquiv &node, uint32_t field_id,
                            const std::vector<size_t> &child_indices) {
        auto &replica = _builder.addEquiv(child_indices.size(), node.getId(), node.getWeight());

        // Visit each child with forced field
        uint32_t saved_field_id = _force_field_id;
        _force_field_id = field_id;
        for (size_t idx : child_indices) {
            node.getChildren()[idx]->accept(*this);
        }
        _force_field_id = saved_field_id;

        // Resolve field metadata from children
        replica.resolveFromChildren(replica.getChildren());
    }

    void visit(ProtonEquiv &node) override {
        // Build map: field_id -> list of original child indices that have this field
        auto field_to_children = buildFieldToChildrenMap(node.getChildren());

        if (field_to_children.empty()) {
            LOG(debug, "field splitting for Equiv node failed: "
                "no fields found in any children (id=%d, weight=%d, num_children=%zu)",
                node.getId(), node.getWeight().percent(), node.getChildren().size());
            vespalib::Issue::report("field splitting for Equiv node failed: "
                                   "no fields found in any children "
                                   "(id=%d, weight=%d, num_children=%zu)",
                                   node.getId(), node.getWeight().percent(),
                                   node.getChildren().size());
            _has_error = true;
            return;
        }

        if (field_to_children.size() == 1) {
            // Only one field - create single Equiv with all children
            const auto& [field_id, child_indices] = *field_to_children.begin();
            createEquivForField(node, field_id, child_indices);
        } else {
            // Multiple fields - create OR with one Equiv per field
            _builder.addOr(field_to_children.size());
            for (const auto& [field_id, child_indices] : field_to_children) {
                createEquivForField(node, field_id, child_indices);
            }
        }
    }

    void visit(ProtonPhrase &node) override {
        splitTerm(node);
    }

    // Helper to determine if SameElement can be split by fields
    bool canSplitSameElement(ProtonSameElement &node) const {
        // Can split if:
        // 1. SameElement has multiple fields
        // 2. All children have the same set of fields as the SameElement
        if (node.numFields() <= 1) {
            return false;
        }
        return allChildrenHaveSameFields(node.getChildren(), getFieldIds(node));
    }

    void visit(ProtonSameElement &node) override {
        if (!canSplitSameElement(node)) {
            LOG(debug, "SameElement not split: has %zu field(s), children have incompatible fields",
                node.numFields());
            handleWithoutSplit(node);
            return;
        }

        // All children have the same multiple fields as SameElement - split like Phrase
        auto fields = getFieldIds(node);
        LOG(debug, "Splitting SameElement across %zu fields", fields.size());
        splitSameElementByFields(node, fields);
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
// Only specialized for term types that don't fit the default pattern

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonRangeTerm>(ProtonRangeTerm &node, size_t field_idx) {
    auto &replica = _builder.addRangeTerm(
        node.getTerm(), getFieldNameOrView(node, field_idx),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonLocationTerm>(ProtonLocationTerm &node, size_t field_idx) {
    auto &replica = _builder.addLocationTerm(
        node.getTerm(), getFieldNameOrView(node, field_idx),
        node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonFuzzyTerm>(ProtonFuzzyTerm &node, size_t field_idx) {
    auto &replica = _builder.addFuzzyTerm(
        node.getTerm(), getFieldNameOrView(node, field_idx),
        node.getId(), node.getWeight(),
        node.max_edit_distance(), node.prefix_lock_length(), node.prefix_match());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonPhrase>(ProtonPhrase &node, size_t field_idx) {
    auto &replica = _builder.addPhrase(node.getChildren().size(), getFieldNameOrView(node, field_idx),
                                      node.getId(), node.getWeight());
    replica.set_expensive(node.is_expensive());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);

    // Process children normally - they won't have fields
    visitNodes(node.getChildren());
}

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonWordAlternatives>(ProtonWordAlternatives &node,
                                                                         size_t field_idx)
{
    // Create ProtonStringTerm children for each alternative
    std::vector<std::unique_ptr<ProtonStringTerm>> children;
    const auto& original_children = node.getChildren();
    children.reserve(original_children.size());

    for (const auto& original_child : original_children) {
        // Create a new ProtonStringTerm for this alternative
        auto child = std::make_unique<ProtonStringTerm>(
            original_child->getTerm(),
            getFieldNameOrView(node, field_idx),
            original_child->getId(),
            original_child->getWeight()
        );
        copyState(*original_child, *child);

        // Copy field entry from parent WordAlternatives to child StringTerm
        if (field_idx < node.numFields()) {
            child->useFieldEntry(node.field(field_idx));
        }

        children.push_back(std::move(child));
    }

    // Create the WordAlternatives replica with ProtonStringTerm children
    auto &replica = _builder.add_word_alternatives(
        std::move(children), getFieldNameOrView(node, field_idx), node.getId(), node.getWeight());
    copyState(node, replica);
    copyProtonTermDataForField(node, replica, field_idx);
}

// Helper macro for multi-term nodes with subterms
#define REPLICATE_MULTITERM(TermType, builderMethod) \
template <> \
void FieldSplitterVisitor::replicateTermForField<TermType>(TermType &node, size_t field_idx) { \
    auto &replica = _builder.builderMethod( \
        replicate_subterms(node), node.getType(), getFieldNameOrView(node, field_idx), \
        node.getId(), node.getWeight()); \
    copyState(node, replica); \
    copyProtonTermDataForField(node, replica, field_idx); \
}

REPLICATE_MULTITERM(ProtonWeightedSetTerm, addWeightedSetTerm)
REPLICATE_MULTITERM(ProtonDotProduct, addDotProduct)
REPLICATE_MULTITERM(ProtonInTerm, add_in_term)

#undef REPLICATE_MULTITERM

template <>
void FieldSplitterVisitor::replicateTermForField<ProtonWandTerm>(ProtonWandTerm &node, size_t field_idx) {
    auto &replica = _builder.addWandTerm(
        replicate_subterms(node), node.getType(), getFieldNameOrView(node, field_idx),
        node.getId(), node.getWeight(),
        node.getTargetNumHits(), node.getScoreThreshold(), node.getThresholdBoostFactor());
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
        LOG(debug, "field splitting failed, returning original tree");
        return root;
    }
    LOG(debug, "field splitting completed, result tree:\n%s", protonTreeToString(*result).c_str());
    return result;
}

}
