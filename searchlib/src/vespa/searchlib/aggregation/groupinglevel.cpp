// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "groupinglevel.h"

#include "grouping.h"

#include <vespa/searchlib/expression/current_index_setup.h>
#include <vespa/searchlib/expression/once_per_document_predicate_node.h>
#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/issue.h>

#include <format>
#include <string_view>
#include <utility>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.aggregation.groupinglevel");

namespace search::aggregation {

using expression::ResultNodeVector;
using vespalib::Deserializer;
using vespalib::Serializer;

IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, GroupingLevel, vespalib::Identifiable);

GroupingLevel::GroupingLevel() noexcept
    : _maxGroups(-1),
      _precision(-1),
      _isOrdered(false),
      _frozen(false),
      _currentIndex(),
      _classify(),
      _collect(),
      _grouper(nullptr) {
}

GroupingLevel::~GroupingLevel() = default;

GroupingLevel::GroupingLevel(const GroupingLevel&) = default;
GroupingLevel::GroupingLevel(GroupingLevel&&) noexcept = default;
GroupingLevel& GroupingLevel::operator=(const GroupingLevel&) = default;
GroupingLevel& GroupingLevel::operator=(GroupingLevel&&) noexcept = default;

Serializer& GroupingLevel::onSerialize(Serializer& os) const {
    return os << _maxGroups << _precision << _classify << _collect;
}

Deserializer& GroupingLevel::onDeserialize(Deserializer& is) {
    return is >> _maxGroups >> _precision >> _classify >> _collect;
}

Serializer& GroupingLevel::serializeVariant(Serializer& os, bool allowV2) const {
    if (allowV2 && _filter.get()) {
        uint32_t cid = CID_search_aggregation_GroupingLevelV2;
        return os << cid << _maxGroups << _precision << _classify << _filter << _collect;
    } else {
        uint32_t cid = CID_search_aggregation_GroupingLevel;
        return onSerialize(os << cid);
    }
}

Deserializer& GroupingLevel::deserializeVariant(Deserializer& is, bool allowV2) {
    uint32_t cid;
    is.get(cid);
    if (allowV2 && (cid == CID_search_aggregation_GroupingLevelV2)) {
        return is >> _maxGroups >> _precision >> _classify >> _filter >> _collect;
    } else {
        // assume old (V1):
        return onDeserialize(is);
    }
}

void GroupingLevel::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "maxGroups", _maxGroups);
    visit(visitor, "precision", _precision);
    visit(visitor, "classify", _classify);
    if (_filter.get()) {
        visit(visitor, "filter", *_filter);
    }
    visit(visitor, "collect", _collect);
}

void GroupingLevel::selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) {
    _classify.select(predicate, operation);
    if (_filter.get()) {
        _filter->select(predicate, operation);
    }
    _collect.select(predicate, operation);
}

GroupingLevel::Grouper::Grouper(const Grouping* grouping, uint32_t level) noexcept
    : _grouping(grouping),
      _level(level),
      _frozen(_level < _grouping->getFirstLevel()),
      _hasNext(_level < _grouping->getLevels().size()),
      _doNext(_level < _grouping->getLastLevel()) {
}

bool GroupingLevel::Grouper::hasNext(size_t level) const noexcept {
    return level < _grouping->getLevels().size();
}

namespace {

// Keep error formatting out of the per-document grouping path.
[[gnu::cold]] void report_filter_error(std::string_view message) {
    vespalib::Issue::report(std::format("Grouping filter error: {}", message));
}

} // namespace

template <bool filtered, typename Doc>
void GroupingLevel::SingleValueGrouper::groupDoc(Group& g, const ResultNode& result, const Doc& doc,
                                                 HitRank rank) const {
    if constexpr (filtered) {
        try {
            if (!_filter.allow(doc, rank)) {
                return;
            }
        } catch (const vespalib::IllegalArgumentException& e) {
            report_filter_error(e.getMessage());
            return;
        } catch (const std::exception& e) {
            // An exception must not escape the match thread, as that would stop the process.
            report_filter_error(e.what());
            return;
        }
    }

    Group* next = g.groupSingle(result, rank, _grouping->getLevels()[_level]);
    if ((next != nullptr) && doNext()) { // do next level ?
        next->aggregate(*_grouping, _level + 1, doc, rank);
    }
}

void GroupingLevel::SingleValueGrouper::group(Group& g, const ResultNode& result, DocId doc, HitRank rank) const {
    groupDoc<true>(g, result, doc, rank);
}

void GroupingLevel::SingleValueGrouper::group(Group& g, const ResultNode& result, const document::Document& doc,
                                              HitRank rank) const {
    groupDoc<true>(g, result, doc, rank);
}

template <bool filtered, typename Doc>
void GroupingLevel::MultiValueGrouper::groupDoc(Group& g, const ResultNode& result, const Doc& doc,
                                                HitRank rank) const {
    const ResultNodeVector& rv = static_cast<const ResultNodeVector&>(result);
    for (size_t i = 0; i < rv.size(); i++) {
        const ResultNode& sr = rv.get(i);
        _currentIndex.set(i);
        SingleValueGrouper::groupDoc<filtered>(g, sr, doc, rank);
    }
}

void GroupingLevel::MultiValueGrouper::group(Group& g, const ResultNode& result, DocId doc, HitRank rank) const {
    groupDoc<true>(g, result, doc, rank);
}

void GroupingLevel::MultiValueGrouper::group(Group& g, const ResultNode& result, const document::Document& doc,
                                             HitRank rank) const {
    groupDoc<true>(g, result, doc, rank);
}

template <typename BaseGrouper>
class GroupingLevel::UnfilteredGrouper : public BaseGrouper {
public:
    using BaseGrouper::BaseGrouper;

    void group(Group& g, const ResultNode& result, DocId doc, HitRank rank) const override {
        BaseGrouper::template groupDoc<false>(g, result, doc, rank);
    }

    void group(Group& g, const ResultNode& result, const document::Document& doc, HitRank rank) const override {
        BaseGrouper::template groupDoc<false>(g, result, doc, rank);
    }

    UnfilteredGrouper* clone() const override { return new UnfilteredGrouper(*this); }
};

template <typename BaseGrouper>
class GroupingLevel::OncePerDocumentGrouper : public BaseGrouper {
    OncePerDocument& _once_per_document;

public:
    template <typename... Args>
    OncePerDocumentGrouper(OncePerDocument& once_per_document, Args&&... args)
        : BaseGrouper(std::forward<Args>(args)...), _once_per_document(once_per_document) {}

    void group(Group& g, const ResultNode& result, DocId doc, HitRank rank) const override {
        _once_per_document.set_parent_group(&g);
        BaseGrouper::group(g, result, doc, rank);
    }

    void group(Group& g, const ResultNode& result, const document::Document& doc, HitRank rank) const override {
        _once_per_document.set_parent_group(&g);
        BaseGrouper::group(g, result, doc, rank);
    }

    OncePerDocumentGrouper* clone() const override { return new OncePerDocumentGrouper(*this); }
};

template <typename BaseGrouper, typename... Args>
GroupingLevel::Grouper* GroupingLevel::make_grouper(OncePerDocument* once_per_document, Args&&... args) {
    if (once_per_document != nullptr) {
        return new OncePerDocumentGrouper<BaseGrouper>(*once_per_document, std::forward<Args>(args)...);
    }
    if (!_filter.get()) {
        return new UnfilteredGrouper<BaseGrouper>(std::forward<Args>(args)...);
    }
    return new BaseGrouper(std::forward<Args>(args)...);
}

void GroupingLevel::wire_current_index(CurrentIndexSetup& setup, const vespalib::ObjectPredicate& resolve_pred,
                                       vespalib::ObjectOperation& resolve_op) {
    CurrentIndexSetup::Usage usage;
    {
        CurrentIndexSetup::Usage::Bind capture_guard(setup, usage);
        _classify.select(resolve_pred, resolve_op);
    }
    if (usage.has_single_unbound_name()) {
        setup.bind(usage.get_unbound_name(), _currentIndex);
    }
    if (_filter.get()) {
        _filter->select(resolve_pred, resolve_op);
    }
    _collect.select(resolve_pred, resolve_op);
}

void GroupingLevel::prepare(const Grouping* grouping, uint32_t level, bool isOrdered_) {
    _isOrdered = isOrdered_;
    _frozen = level < grouping->getFirstLevel();
    auto* once_per_document =
        _filter.get() ? expression::OncePerDocumentPredicateNode::find_last_condition(*_filter) : nullptr;
    if (_classify.getResult()->inherits(ResultNodeVector::classId)) {
        _grouper.reset(make_grouper<MultiValueGrouper>(
            once_per_document, _currentIndex, getActiveFilter(), grouping, level));
    } else {
        _grouper.reset(make_grouper<SingleValueGrouper>(once_per_document, getActiveFilter(), grouping, level));
    }
}

// template<> void GroupingLevel::MultiValueGrouper::groupDoc(Group & g, const ResultNode::CP & result, const
// document::Document & doc, HitRank rank, bool isOrdered) const; template<> void
// GroupingLevel::MultiValueGrouper::groupDoc(Group & g, const ResultNode::CP & result, DocId doc, HitRank rank, bool
// isOrdered) const;

} // namespace search::aggregation

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_groupinglevel() {
}
