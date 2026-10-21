// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlated_grouping_filters.h"
#include "match_tools.h"
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchlib/expression/correlated_predicate_node.h>
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/queryeval/correlate_by_blueprint.h>
#include <vespa/searchlib/queryeval/correlated_bindings.h>
#include <vespa/searchlib/queryeval/correlated_binding_provider.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/util/issue.h>

#include <algorithm>
#include <map>

namespace proton::matching {

namespace {

using search::expression::CorrelatedPredicateNode;
using search::queryeval::Blueprint;
using search::queryeval::CorrelateByBlueprint;
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

class BindingProviderFactory : public CorrelatedBindingFactory {
    const CorrelateByBlueprint& _blueprint;
    search::fef::MatchDataLayout _layout;
    uint32_t _docid_limit;

public:
    BindingProviderFactory(const CorrelateByBlueprint& blueprint, search::fef::MatchDataLayout layout,
                           uint32_t docid_limit)
        : _blueprint(blueprint), _layout(std::move(layout)), _docid_limit(docid_limit) {}
    const std::map<std::string, uint32_t>& columns() const override { return _blueprint.columns(); }
    std::vector<bool> uncased_columns() const override { return _blueprint.uncased_columns(); }
    std::unique_ptr<CorrelatedBindingReader> create() const override {
        return std::make_unique<search::queryeval::CorrelatedBindingProvider>(_blueprint, _layout, _docid_limit);
    }
};

bool reject_all_rows(uint32_t, std::span<const search::CorrelationBinding>) noexcept { return false; }

struct FindFilters : vespalib::ObjectPredicate, vespalib::ObjectOperation {
    std::vector<CorrelatedPredicateNode*>& filters;
    explicit FindFilters(std::vector<CorrelatedPredicateNode*>& target) : filters(target) {}
    bool check(const vespalib::Identifiable& object) const override { return object.inherits(CorrelatedPredicateNode::classId); }
    void execute(vespalib::Identifiable& object) override { filters.push_back(static_cast<CorrelatedPredicateNode*>(&object)); }
};

} // namespace

void prepare_correlated_bindings(const Query& query, search::fef::MatchDataLayout& layout,
                                search::fef::IObjectStore& store, uint32_t docid_limit) {
    std::map<std::string, const CorrelateByBlueprint*> blueprints;
    if (query.peekRoot()) find_blueprints(*query.peekRoot(), blueprints);
    if (blueprints.empty()) return;
    std::map<std::string, uint32_t> labels;
    if (query.query_tree_root()) find_query_labels(*query.query_tree_root(), labels);
    for (const auto& [label, blueprint] : blueprints) {
        if (labels[label] == 1)
            store.add(CorrelatedBindingFactory::store_key(label),
                      std::make_unique<BindingProviderFactory>(*blueprint, layout.copy(), docid_limit));
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
    // Check each filter once here, so a bad request costs one issue, not one error per row.
    // Issues with the same text from several threads are reported once.
    for (auto* filter : _filters) {
        const std::string& label = filter->label();
        uint32_t label_count = labels[label];
        if (label_count != 1) {
            vespalib::Issue::report("Grouping filter error: correlated('%s') needs exactly one correlateBy with this "
                                    "label in the query, but there are %u", label.c_str(), label_count);
            matchers.emplace_back(reject_all_rows);
            continue;
        }
        const auto* provider_factory = dynamic_cast<const CorrelatedBindingFactory*>(factory.object_store()->get(
                CorrelatedBindingFactory::store_key(label)));
        if (!provider_factory) {
            // A known labeled query may have been optimized to an empty search.
            matchers.emplace_back(reject_all_rows);
            continue;
        }
        std::vector<uint32_t> projection;
        std::vector<bool> uncased;
        std::string unbound;
        for (const auto& variable : filter->variables()) {
            auto column = provider_factory->columns().find(variable);
            if (column == provider_factory->columns().end()) {
                unbound = variable;
                break;
            }
            projection.push_back(column->second);
        }
        auto uncased_columns = provider_factory->uncased_columns();
        for (uint32_t column : projection) uncased.push_back(column < uncased_columns.size() && uncased_columns[column]);
        if (!unbound.empty()) {
            vespalib::Issue::report("Grouping filter error: correlated('%s') uses variable '%s', which the query "
                                    "does not bind", label.c_str(), unbound.c_str());
            matchers.emplace_back(reject_all_rows);
            continue;
        }
        auto& provider = providers[label];
        if (!provider) provider = provider_factory->create();
        matchers.emplace_back([provider, projection = std::move(projection), uncased = std::move(uncased),
                               values = search::queryeval::BindingRow{}, strings = std::vector<std::string>{}]
                               (uint32_t docid, std::span<const search::CorrelationBinding> projected) mutable {
            auto bindings = provider->read(docid, projection);
            values.clear();
            strings.resize(projected.size());
            for (size_t i = 0; i < projected.size(); ++i) {
                const auto& value = projected[i].second;
                if (uncased[i] && std::holds_alternative<std::string_view>(value)) {
                    strings[i] = vespalib::LowerCase::convert(std::get<std::string_view>(value));
                    values.emplace_back(std::string_view(strings[i]));
                } else {
                    values.push_back(value);
                }
            }
            return std::binary_search(bindings.begin(), bindings.end(), values);
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
