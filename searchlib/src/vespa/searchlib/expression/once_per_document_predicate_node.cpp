// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "once_per_document_predicate_node.h"
#include "and_predicate_node.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>
#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <functional>
#include <limits>

namespace search::expression {

IMPLEMENT_IDENTIFIABLE_NS2(search, expression, OncePerDocumentPredicateNode, FilterPredicateNode);

bool OncePerDocumentPredicateNode::EntryLess::operator()(uint32_t a, uint32_t b) const {
    if (self->_parents[a] != self->_parents[b]) return std::less<const void*>()(self->_parents[a], self->_parents[b]);
    size_t width = self->_arguments.size();
    for (size_t i = 0; i < width; ++i) {
        int comparison = self->_values[a * width + i]->cmp(*self->_values[b * width + i]);
        if (comparison != 0) return comparison < 0;
    }
    return false;
}

OncePerDocumentPredicateNode::OncePerDocumentPredicateNode()
    : _arguments(), _parents(), _values(), _seen(EntryLess{this}), _docid(std::numeric_limits<DocId>::max()),
      _parent_group(nullptr), _misplaced(false), _error_reported(false) {}
OncePerDocumentPredicateNode::~OncePerDocumentPredicateNode() = default;
OncePerDocumentPredicateNode::OncePerDocumentPredicateNode(const OncePerDocumentPredicateNode& other)
    : FilterPredicateNode(other), _arguments(other._arguments), _parents(), _values(), _seen(EntryLess{this}),
      _docid(std::numeric_limits<DocId>::max()), _parent_group(nullptr), _misplaced(other._misplaced),
      _error_reported(false) {}

OncePerDocumentPredicateNode& OncePerDocumentPredicateNode::operator=(const OncePerDocumentPredicateNode& other) {
    if (this != &other) {
        FilterPredicateNode::operator=(other);
        _arguments = other._arguments;
        _values.clear();
        reset_seen();
        _parent_group = nullptr;
        _misplaced = other._misplaced;
        _error_reported = false;
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

void OncePerDocumentPredicateNode::reset_seen() {
    _seen.clear();
    _parents.clear();
    _docid = std::numeric_limits<DocId>::max();
}

void OncePerDocumentPredicateNode::store_value(size_t slot, const ResultNode& value) {
    if (slot == _values.size()) {
        _values.emplace_back(value.clone());
    } else if (_values[slot]->getClass().id() == value.getClass().id()) {
        _values[slot]->set(value);
    } else {
        _values[slot].reset(value.clone());
    }
}

bool OncePerDocumentPredicateNode::reject_row(const char* message) {
    if (!_error_reported) {
        _error_reported = true;
        vespalib::Issue::report("Grouping filter error: onceperdoc: %s", message);
    }
    return false;
}

bool OncePerDocumentPredicateNode::allow(DocId docid, HitRank rank) {
    if (_misplaced) return reject_row("it must be the whole filter or the last condition of 'and'");
    if (_docid != docid) {
        reset_seen();
        _docid = docid;
    }
    auto entry = static_cast<uint32_t>(_parents.size());
    size_t width = _arguments.size();
    for (size_t i = 0; i < width; ++i) {
        auto& expression = _arguments[i];
        expression.execute(docid, rank);
        const auto* result = expression.getResult();
        if (result->isMultiValue()) return reject_row("keys must be single values of the grouped row");
        store_value(entry * width + i, *result);
    }
    _parents.push_back(_parent_group);
    if (_seen.insert(entry).second) return true;
    _parents.pop_back();
    return false;
}

bool OncePerDocumentPredicateNode::allow(const document::Document&, HitRank) {
    return reject_row("streaming search is not supported");
}

OncePerDocumentPredicateNode* OncePerDocumentPredicateNode::find_last_condition(FilterPredicateNode& filter) {
    struct FindAll : vespalib::ObjectPredicate, vespalib::ObjectOperation {
        std::vector<OncePerDocumentPredicateNode*> found;
        bool check(const vespalib::Identifiable& object) const override {
            return object.inherits(OncePerDocumentPredicateNode::classId);
        }
        void execute(vespalib::Identifiable& object) override {
            found.push_back(static_cast<OncePerDocumentPredicateNode*>(&object));
        }
    } finder;
    filter.select(finder, finder);
    FilterPredicateNode* last = &filter;
    while (auto* conjunction = dynamic_cast<AndPredicateNode*>(last)) {
        if (conjunction->args().empty()) break;
        last = conjunction->args().back().get();
    }
    auto* allowed = dynamic_cast<OncePerDocumentPredicateNode*>(last);
    for (auto* node : finder.found) node->set_misplaced(node != allowed);
    return allowed;
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
    _values.clear();
    reset_seen();
    _error_reported = false;
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
