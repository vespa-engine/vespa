// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlated_grouping_filters.h"
#include "match_tools.h"
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchlib/expression/correlated_predicate_node.h>
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/queryeval/correlate_by_blueprint.h>
#include <vespa/searchlib/queryeval/correlated_bindings.h>
#include <vespa/vespalib/util/exceptions.h>

#include <algorithm>
#include <limits>
#include <map>

namespace proton::matching {

namespace {

using search::expression::CorrelatedPredicateNode;
using search::queryeval::Blueprint;
using search::queryeval::CorrelateByBlueprint;
using search::queryeval::CorrelateBySearch;
using search::queryeval::CorrelatedBindingFactory;
using search::queryeval::CorrelatedBindingReader;

void find_blueprints(const Blueprint& bp, std::map<std::string, const CorrelateByBlueprint*>& result) {
    if (auto* correlated = dynamic_cast<const CorrelateByBlueprint*>(&bp)) {
        if (!correlated->label().empty()) result.emplace(correlated->label(), correlated);
    }
    if (auto* intermediate = dynamic_cast<const search::queryeval::IntermediateBlueprint*>(&bp)) {
        for (size_t i = 0; i < intermediate->childCnt(); ++i) find_blueprints(intermediate->getChild(i), result);
    }
}

void find_query_labels(const search::query::Node& node, std::map<std::string, uint32_t>& labels) {
    if (auto* correlated = dynamic_cast<const search::query::CorrelateBy*>(&node)) {
        if (!correlated->label().empty()) ++labels[correlated->label()];
    }
    if (auto* intermediate = dynamic_cast<const search::query::Intermediate*>(&node)) {
        for (auto* child : intermediate->getChildren()) find_query_labels(*child, labels);
    }
}

class BindingProvider : public CorrelatedBindingReader {
    search::fef::MatchData::UP _match_data;
    std::unique_ptr<CorrelateBySearch> _search;
    uint32_t _docid = std::numeric_limits<uint32_t>::max();
    std::vector<uint32_t> _projection;
    const std::vector<search::queryeval::BindingRow>* _bindings = nullptr;

public:
    BindingProvider(const CorrelateByBlueprint& bp, const search::fef::MatchDataLayout& layout)
        : _match_data(layout.createMatchData()), _search(bp.create_correlate_search(*_match_data)) {}

    std::span<const search::queryeval::BindingRow> read(uint32_t docid, std::span<const uint32_t> projection) override {
        if (!_search) return {};
        bool same_document = _docid == docid;
        if (!same_document || !std::equal(projection.begin(), projection.end(), _projection.begin(), _projection.end())) {
            // Ranking and grouping can visit documents in relevance order, so reset the
            // independent iterator before reading a document out of order.
            if (!same_document) _search->initRange(docid, docid + 1);
            _bindings = &_search->find_bindings(docid, projection);
            _projection.assign(projection.begin(), projection.end());
            _docid = docid;
        }
        return *_bindings;
    }
};

class BindingProviderFactory : public CorrelatedBindingFactory {
    const CorrelateByBlueprint& _blueprint;
    search::fef::MatchDataLayout _layout;

public:
    BindingProviderFactory(const CorrelateByBlueprint& blueprint, search::fef::MatchDataLayout layout)
        : _blueprint(blueprint), _layout(std::move(layout)) {}
    const std::map<std::string, uint32_t>& columns() const override { return _blueprint.columns(); }
    std::unique_ptr<CorrelatedBindingReader> create() const override {
        return std::make_unique<BindingProvider>(_blueprint, _layout);
    }
};

struct FindFilters : vespalib::ObjectPredicate, vespalib::ObjectOperation {
    std::vector<CorrelatedPredicateNode*>& filters;
    explicit FindFilters(std::vector<CorrelatedPredicateNode*>& target) : filters(target) {}
    bool check(const vespalib::Identifiable& object) const override { return object.inherits(CorrelatedPredicateNode::classId); }
    void execute(vespalib::Identifiable& object) override { filters.push_back(static_cast<CorrelatedPredicateNode*>(&object)); }
};

} // namespace

void prepare_correlated_bindings(const Query& query, search::fef::MatchDataLayout& layout,
                                search::fef::IObjectStore& store) {
    std::map<std::string, const CorrelateByBlueprint*> blueprints;
    if (query.peekRoot()) find_blueprints(*query.peekRoot(), blueprints);
    std::map<std::string, uint32_t> labels;
    if (query.query_tree_root()) find_query_labels(*query.query_tree_root(), labels);
    for (const auto& [label, blueprint] : blueprints) {
        if (labels[label] == 1)
            store.add(CorrelatedBindingFactory::store_key(label),
                      std::make_unique<BindingProviderFactory>(*blueprint, layout.copy()));
    }
}

CorrelatedGroupingFilters::CorrelatedGroupingFilters(search::grouping::GroupingContext& grouping,
                                                     const MatchToolsFactory& factory) : _filters() {
    FindFilters finder(_filters);
    for (const auto& request : grouping.getGroupingList()) request->select(finder, finder);
    if (_filters.empty()) return;
    std::map<std::string, uint32_t> labels;
    if (factory.query().query_tree_root()) find_query_labels(*factory.query().query_tree_root(), labels);
    std::map<std::string, std::shared_ptr<CorrelatedBindingReader>> providers;
    std::vector<CorrelatedPredicateNode::Matcher> matchers;
    for (auto* filter : _filters) {
        if (labels[filter->label()] != 1) {
            auto message = "correlated grouping requires exactly one query with label '" + filter->label() + "'";
            matchers.emplace_back([message](uint32_t, std::span<const search::CorrelationBinding>) -> bool {
                throw vespalib::IllegalArgumentException(message);
            });
            continue;
        }
        const auto* provider_factory = dynamic_cast<const CorrelatedBindingFactory*>(factory.object_store()->get(
                CorrelatedBindingFactory::store_key(filter->label())));
        if (!provider_factory) {
            // A known labeled query may have been optimized to an empty search.
            matchers.emplace_back([](uint32_t, std::span<const search::CorrelationBinding>) noexcept { return false; });
            continue;
        }
        auto& provider = providers[filter->label()];
        if (!provider) provider = provider_factory->create();
        matchers.emplace_back([provider, columns = provider_factory->columns(), projection = std::vector<uint32_t>{}]
                               (uint32_t docid, std::span<const search::CorrelationBinding> projected) mutable {
            if (projection.empty()) {
                std::vector<uint32_t> requested;
                for (const auto& [variable, value] : projected) {
                    if (!columns.contains(variable))
                        throw vespalib::IllegalArgumentException("Grouping refers to an unbound correlation variable '" + variable + "'");
                    requested.push_back(columns.at(variable));
                }
                projection = std::move(requested);
            }
            auto bindings = provider->read(docid, projection);
            return std::any_of(bindings.begin(), bindings.end(), [&](const auto& binding) {
                for (size_t i = 0; i < projected.size(); ++i)
                    if (binding[i] != projected[i].second) return false;
                return true;
            });
        });
    }
    // Prepare every provider before installing callbacks so a failed setup
    // cannot leave a cached grouping request holding query-owned attributes.
    for (size_t i = 0; i < _filters.size(); ++i) _filters[i]->set_matcher(std::move(matchers[i]));
}

CorrelatedGroupingFilters::~CorrelatedGroupingFilters() {
    for (auto* filter : _filters) filter->set_matcher({});
}

} // namespace proton::matching
