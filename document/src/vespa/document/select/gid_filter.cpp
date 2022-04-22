// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "gid_filter.h"
#include "node.h"
#include "visitor.h"
#include "valuenodes.h"
#include "compare.h"
#include "branch.h"
#include <vespa/document/base/idstring.h>

namespace document::select {

namespace {

struct NoOpVisitor : Visitor {
    void visitAndBranch(const And&) override {}
    void visitComparison(const Compare&) override {}
    void visitConstant(const Constant&) override {}
    void visitInvalidConstant(const InvalidConstant&) override {}
    void visitDocumentType(const DocType&) override {}
    void visitNotBranch(const Not&) override {}
    void visitOrBranch(const Or&) override {}
    void visitArithmeticValueNode(const ArithmeticValueNode&) override {}
    void visitFunctionValueNode(const FunctionValueNode&) override {}
    void visitIdValueNode(const IdValueNode&) override {}
    void visitFieldValueNode(const FieldValueNode&) override {}
    void visitFloatValueNode(const FloatValueNode&) override {}
    void visitVariableValueNode(const VariableValueNode&) override {}
    void visitIntegerValueNode(const IntegerValueNode&) override {}
    void visitBoolValueNode(const BoolValueNode&) override {}
    void visitCurrentTimeValueNode(const CurrentTimeValueNode&) override {}
    void visitStringValueNode(const StringValueNode&) override {}
    void visitNullValueNode(const NullValueNode&) override {}
    void visitInvalidValueNode(const InvalidValueNode&) override {}
};

/**
 * Used for identifying whether a given visited node is a comparison node for a
 * location constraint, and if so, what the location constraint parameters
 * actually are. Only visits the children of the provided node.
 *
 * Allows for child nodes to be in any order to allow order-invariant
 * (commuting) comparisons (i.e. "a == b" is identical to "b == a")
 */
struct IdComparisonVisitor : NoOpVisitor {
    const IdValueNode*      _id_user_node{nullptr};
    const IdValueNode*      _id_group_node{nullptr};
    const IntegerValueNode* _int_literal_node{nullptr};
    const StringValueNode*  _string_literal_node{nullptr};

    void visitIdValueNode(const IdValueNode& node) override {
        const auto type = node.getType();
        if (type == IdValueNode::USER) {
            _id_user_node = &node;
        } else if (type == IdValueNode::GROUP) {
            _id_group_node = &node;
        }
    }

    void visitIntegerValueNode(const IntegerValueNode& node) override {
        _int_literal_node = &node;
    }

    void visitStringValueNode(const StringValueNode& node) override {
        _string_literal_node = &node;
    }

    bool is_valid_location_sub_expression() const noexcept {
        return ((_id_user_node && _int_literal_node)
                || (_id_group_node && _string_literal_node));
    }
};

/**
 * Base visitor type invariant: it MUST NOT descend further down the tree by
 * default for any inner node.
 */
class LocationConstraintVisitor : public NoOpVisitor {
    GidFilter::OptionalLocation _location;
public:
    GidFilter::OptionalLocation location() const noexcept { return _location; }
private:
    void visitAndBranch(const And& node) override {
        node.getLeft().visit(*this);
        node.getRight().visit(*this);
    }

    /**
     * We explicitly DO NOT visit OR/NOT branches here. This implicitly
     * causes the DFS of the AST to terminate early and does not attempt to
     * identify any location predicates further down the tree. This means that
     * we only process location predicates that are _directly_ reachable from
     * the root node via 0-n AND branches and therefore must be matched in
     * order for the whole selection to match. The default behavior when
     * we cannot find a location predicate is to assume all documents may match,
     * which is the correct behavior in any other case, as we can no longer
     * guarantee that not matching the GID will cause the selection itself to
     * also mismatch.
     */

    void visitComparison(const Compare& cmp) override {
        IdComparisonVisitor id_visitor;
        cmp.getLeft().visit(id_visitor);
        cmp.getRight().visit(id_visitor);
        if (!id_visitor.is_valid_location_sub_expression()) {
            return; // Don't bother visiting any subtrees.
        }
        extract_location_from_id_visitor(id_visitor);
    }

    uint32_t truncate_location(int64_t full_location) const noexcept {
        return static_cast<uint32_t>(full_location);
    }

    uint32_t location_from_integer_literal_node(
            const IntegerValueNode& node) const
    {
        Context ctx;
        auto rhs = node.getValue(ctx);
        auto full_location = static_cast<const IntegerValue&>(*rhs).getValue();
        return truncate_location(full_location);
    }

    uint32_t location_from_string_literal_node(
            const StringValueNode& node) const
    {
        auto full_location = IdString::makeLocation(node.getValue());
        return truncate_location(full_location);
    }

    void extract_location_from_id_visitor(const IdComparisonVisitor& visitor) {
        uint32_t location;
        if (visitor._int_literal_node) {
            location = location_from_integer_literal_node(
                    *visitor._int_literal_node);
        } else {
            location = location_from_string_literal_node(
                    *visitor._string_literal_node);
        }
        _location = GidFilter::OptionalLocation(location);
    }
};

GidFilter::OptionalLocation location_bits_from_selection(const Node& ast_root) {
    LocationConstraintVisitor visitor;
    ast_root.visit(visitor);
    return visitor.location();
}

} // anon ns

GidFilter::GidFilter(const Node& ast_root)
    : _required_gid_location(location_bits_from_selection(ast_root))
{
}

}
