// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "once_per_document_predicate_node.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <algorithm>
#include <limits>

namespace search::expression {

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, OncePerDocumentPredicateNode, FilterPredicateNode);

bool OncePerDocumentPredicateNode::KeyLess::operator()(const Key& a, const Key& b) const {
    for (size_t i = 0; i < std::min(a.size(), b.size()); ++i) {
        int comparison = a[i]->cmp(*b[i]);
        if (comparison != 0) return comparison < 0;
    }
    return a.size() < b.size();
}

OncePerDocumentPredicateNode::OncePerDocumentPredicateNode()
    : _arguments(), _seen(), _docid(std::numeric_limits<DocId>::max()) {}
OncePerDocumentPredicateNode::~OncePerDocumentPredicateNode() = default;
OncePerDocumentPredicateNode::OncePerDocumentPredicateNode(const OncePerDocumentPredicateNode& other)
    : FilterPredicateNode(other), _arguments(other._arguments), _seen(), _docid(std::numeric_limits<DocId>::max()) {}

OncePerDocumentPredicateNode& OncePerDocumentPredicateNode::operator=(const OncePerDocumentPredicateNode& other) {
    if (this != &other) {
        FilterPredicateNode::operator=(other);
        _arguments = other._arguments;
        _seen.clear();
        _docid = std::numeric_limits<DocId>::max();
    }
    return *this;
}

OncePerDocumentPredicateNode::OncePerDocumentPredicateNode(std::vector<ExpressionNode::UP> keys)
    : OncePerDocumentPredicateNode() {
    if (keys.empty()) throw vespalib::IllegalArgumentException("onceperdoc requires at least one key");
    for (auto& key : keys) {
        if (!key) throw vespalib::IllegalArgumentException("onceperdoc requires non-null key expressions");
        _arguments.emplace_back(std::move(key));
    }
}

bool OncePerDocumentPredicateNode::allow(DocId docid, HitRank rank) {
    if (_docid != docid) {
        _seen.clear();
        _docid = docid;
    }
    Key key;
    for (auto& expression : _arguments) {
        expression.execute(docid, rank);
        const auto* result = expression.getResult();
        if (result->isMultiValue())
            throw vespalib::IllegalArgumentException("onceperdoc keys must be scalar expressions bound to a grouping row");
        key.emplace_back(result->clone());
    }
    return _seen.insert(std::move(key)).second;
}

bool OncePerDocumentPredicateNode::allow(const document::Document&, HitRank) {
    throw vespalib::IllegalArgumentException("onceperdoc grouping requires indexed search");
}

vespalib::Serializer& OncePerDocumentPredicateNode::onSerialize(vespalib::Serializer& os) const {
    os << uint32_t(_arguments.size());
    for (const auto& argument : _arguments) os << argument;
    return os;
}

vespalib::Deserializer& OncePerDocumentPredicateNode::onDeserialize(vespalib::Deserializer& is) {
    uint32_t count = 0;
    is >> count;
    if (count == 0) throw vespalib::IllegalArgumentException("onceperdoc requires at least one key");
    _arguments.clear();
    _seen.clear();
    _docid = std::numeric_limits<DocId>::max();
    for (uint32_t i = 0; i < count; ++i) {
        ExpressionTree argument;
        is >> argument;
        if (!argument.getRoot()) throw vespalib::IllegalArgumentException("onceperdoc requires non-null key expressions");
        _arguments.push_back(std::move(argument));
    }
    return is;
}

void OncePerDocumentPredicateNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "arguments", _arguments);
}

void OncePerDocumentPredicateNode::selectMembers(const vespalib::ObjectPredicate& predicate,
                                                vespalib::ObjectOperation& operation) {
    for (auto& argument : _arguments) argument.select(predicate, operation);
}

} // namespace search::expression
