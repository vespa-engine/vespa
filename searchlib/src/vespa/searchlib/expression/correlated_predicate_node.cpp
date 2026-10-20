// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlated_predicate_node.h"
#include "attributenode.h"
#include "resultnode.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>
#include <set>

namespace search::expression {

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, CorrelatedPredicateNode, FilterPredicateNode);

CorrelatedPredicateNode::CorrelatedPredicateNode()
    : _label(), _fields(), _matcher(), _bindings(), _strings(), _error_reported(false) {}
CorrelatedPredicateNode::~CorrelatedPredicateNode() = default;
CorrelatedPredicateNode::CorrelatedPredicateNode(const CorrelatedPredicateNode& other)
    : FilterPredicateNode(other), _label(other._label), _fields(other._fields), _matcher(), _bindings(), _strings(),
      _error_reported(false) {
    prepare_bindings();
}

CorrelatedPredicateNode& CorrelatedPredicateNode::operator=(const CorrelatedPredicateNode& other) {
    if (this != &other) {
        FilterPredicateNode::operator=(other);
        _label = other._label;
        _fields = other._fields;
        _matcher = {};
        _error_reported = false;
        prepare_bindings();
    }
    return *this;
}

CorrelatedPredicateNode::CorrelatedPredicateNode(std::string label, std::vector<std::string> variables,
                                               std::vector<ExpressionNode::UP> fields)
    : CorrelatedPredicateNode() {
    _label = std::move(label);
    if (_label.empty() || variables.empty() || variables.size() != fields.size())
        throw vespalib::IllegalArgumentException("correlated requires variable/attribute pairs");
    std::set<std::string> seen;
    for (size_t i = 0; i < variables.size(); ++i) {
        if (variables[i].empty() || !seen.insert(variables[i]).second || !dynamic_cast<AttributeNode*>(fields[i].get()))
            throw vespalib::IllegalArgumentException("correlated requires distinct variables and attribute arguments");
        _fields.emplace_back(std::move(variables[i]), ExpressionTree(std::move(fields[i])));
    }
    prepare_bindings();
}

void CorrelatedPredicateNode::prepare_bindings() {
    _bindings.clear();
    for (const auto& [variable, field] : _fields) _bindings.emplace_back(variable, int64_t(0));
    _strings.assign(_fields.size(), std::string());
}

std::vector<std::string> CorrelatedPredicateNode::variables() const {
    std::vector<std::string> result;
    result.reserve(_fields.size());
    for (const auto& [variable, field] : _fields) result.push_back(variable);
    return result;
}

bool CorrelatedPredicateNode::reject_row(const char* message) {
    if (!_error_reported) {
        _error_reported = true;
        vespalib::Issue::report("Grouping filter error: correlated('%s'): %s", _label.c_str(), message);
    }
    return false;
}

bool CorrelatedPredicateNode::allow(DocId docid, HitRank rank) {
    if (!_matcher) return reject_row("there is no query to read bindings from");
    for (size_t i = 0; i < _fields.size(); ++i) {
        auto& expression = _fields[i].second;
        auto* field = dynamic_cast<AttributeNode*>(expression.getRoot());
        if (!field || !field->getAttribute()) return reject_row("arguments must be attributes");
        expression.execute(docid, rank);
        auto* result = expression.getResult();
        if (result->isMultiValue()) return reject_row("attributes must be fields of the grouped struct array");
        if (field->getAttribute()->isStringType()) {
            HoldString value(*result);
            _strings[i].assign(value.data(), value.size());
            _bindings[i].second = std::string_view(_strings[i]);
        } else if (field->getAttribute()->isIntegerType()) {
            _bindings[i].second = result->getInteger();
        } else {
            return reject_row("attributes must be integers or strings");
        }
    }
    return _matcher(docid, _bindings);
}

bool CorrelatedPredicateNode::allow(const document::Document&, HitRank) {
    return reject_row("streaming search is not supported");
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
    _error_reported = false;
    prepare_bindings();
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
