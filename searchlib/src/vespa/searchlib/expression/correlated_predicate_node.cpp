// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlated_predicate_node.h"
#include "attributenode.h"
#include "resultnode.h"
#include <vespa/vespalib/util/exceptions.h>
#include <set>

namespace search::expression {

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, CorrelatedPredicateNode, FilterPredicateNode);

CorrelatedPredicateNode::CorrelatedPredicateNode() : _label(), _fields(), _matcher() {}
CorrelatedPredicateNode::~CorrelatedPredicateNode() = default;
CorrelatedPredicateNode::CorrelatedPredicateNode(const CorrelatedPredicateNode& other)
    : FilterPredicateNode(other), _label(other._label), _fields(other._fields), _matcher() {}

CorrelatedPredicateNode& CorrelatedPredicateNode::operator=(const CorrelatedPredicateNode& other) {
    if (this != &other) {
        FilterPredicateNode::operator=(other);
        _label = other._label;
        _fields = other._fields;
        _matcher = {};
    }
    return *this;
}

CorrelatedPredicateNode::CorrelatedPredicateNode(std::string label, std::vector<std::string> variables,
                                               std::vector<ExpressionNode::UP> fields)
    : _label(std::move(label)), _fields(), _matcher() {
    if (_label.empty() || variables.empty() || variables.size() != fields.size())
        throw vespalib::IllegalArgumentException("correlated requires variable/attribute pairs");
    std::set<std::string> seen;
    for (size_t i = 0; i < variables.size(); ++i) {
        if (variables[i].empty() || !seen.insert(variables[i]).second || !dynamic_cast<AttributeNode*>(fields[i].get()))
            throw vespalib::IllegalArgumentException("correlated requires distinct variables and attribute arguments");
        _fields.emplace_back(std::move(variables[i]), ExpressionTree(std::move(fields[i])));
    }
}

bool CorrelatedPredicateNode::allow(DocId docid, HitRank rank) {
    if (!_matcher) throw vespalib::IllegalArgumentException("correlated grouping has no query binding provider");
    CorrelationBindings bindings;
    bindings.reserve(_fields.size());
    for (auto& [variable, expression] : _fields) {
        auto* field = dynamic_cast<AttributeNode*>(expression.getRoot());
        if (!field || !field->getAttribute())
            throw vespalib::IllegalArgumentException("correlated grouping requires attribute arguments");
        expression.execute(docid, rank);
        auto* result = expression.getResult();
        if (result->isMultiValue())
            throw vespalib::IllegalArgumentException("correlated grouping attributes must be bound to a grouping row");
        if (field->getAttribute()->isStringType()) {
            HoldString value(*result);
            bindings.emplace_back(variable, std::string_view(value.data(), value.size()));
        } else if (field->getAttribute()->isIntegerType()) {
            bindings.emplace_back(variable, result->getInteger());
        } else {
            throw vespalib::IllegalArgumentException("correlated grouping bindings must be integers or strings");
        }
    }
    return _matcher(docid, bindings);
}

bool CorrelatedPredicateNode::allow(const document::Document&, HitRank) {
    throw vespalib::IllegalArgumentException("correlated grouping requires indexed search");
}

vespalib::Serializer& CorrelatedPredicateNode::onSerialize(vespalib::Serializer& os) const {
    os << _label << uint32_t(_fields.size());
    for (const auto& [variable, field] : _fields) os << variable << field;
    return os;
}

vespalib::Deserializer& CorrelatedPredicateNode::onDeserialize(vespalib::Deserializer& is) {
    uint32_t count = 0;
    is >> _label >> count;
    if (_label.empty() || count == 0)
        throw vespalib::IllegalArgumentException("correlated requires a label and variable/attribute pairs");
    _fields.clear();
    _matcher = {};
    std::set<std::string> seen;
    for (uint32_t i = 0; i < count; ++i) {
        std::string variable;
        ExpressionTree field;
        is >> variable >> field;
        if (variable.empty() || !seen.insert(variable).second || !dynamic_cast<AttributeNode*>(field.getRoot()))
            throw vespalib::IllegalArgumentException("correlated requires distinct variables and attribute arguments");
        _fields.emplace_back(std::move(variable), std::move(field));
    }
    return is;
}

void CorrelatedPredicateNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "label", _label);
    for (const auto& [variable, field] : _fields) visit(visitor, variable, field);
}

void CorrelatedPredicateNode::selectMembers(const vespalib::ObjectPredicate& predicate,
                                           vespalib::ObjectOperation& operation) {
    for (auto& [variable, field] : _fields) field.select(predicate, operation);
}

} // namespace search::expression
