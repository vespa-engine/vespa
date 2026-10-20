// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "correlated_grouping_filters.h"
#include "match_tools.h"
#include <vespa/searchcore/grouping/groupingcontext.h>
#include <vespa/searchlib/expression/correlated_predicate_node.h>
#include <vespa/searchlib/query/tree/intermediatenodes.h>
#include <vespa/searchlib/queryeval/correlate_by_blueprint.h>
#include <vespa/searchlib/queryeval/correlated_bindings.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/util/issue.h>

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

/**
 * Reads the bindings of one labeled query for one thread. All complete bindings of a
 * document are found once, then projected for each caller, so filters that use
 * different variables of the same query share the work.
 */
class BindingProvider : public CorrelatedBindingReader {
    struct Projection {
        std::vector<uint32_t> columns;
        std::vector<search::queryeval::BindingRow> rows;
        bool valid = false;
    };
    search::fef::MatchData::UP _match_data;
    std::unique_ptr<CorrelateBySearch> _search;
    uint32_t _docid_limit;
    uint32_t _docid = std::numeric_limits<uint32_t>::max();
    const std::vector<search::queryeval::BindingRow>* _rows = nullptr;
    std::vector<Projection> _projections;

    void read_document(uint32_t docid) {
        // Iterators only move forward. Ranking and grouping can go back to an earlier
        // document, and only then must the iterators start over.
        if (_docid == std::numeric_limits<uint32_t>::max() || docid < _docid) {
            _search->initRange(docid, _docid_limit);
        }
        _rows = &_search->find_bindings(docid);
        _docid = docid;
        for (auto& projection : _projections) projection.valid = false;
    }

    Projection& projection_for(std::span<const uint32_t> columns) {
        for (auto& projection : _projections) {
            if (std::equal(projection.columns.begin(), projection.columns.end(), columns.begin(), columns.end())) {
                return projection;
            }
        }
        auto& projection = _projections.emplace_back();
        projection.columns.assign(columns.begin(), columns.end());
        return projection;
    }

    void project(Projection& projection) {
        projection.rows.clear();
        for (const auto& row : *_rows) {
            auto& projected = projection.rows.emplace_back();
            projected.reserve(projection.columns.size());
            for (uint32_t column : projection.columns) projected.push_back(row[column]);
        }
        std::sort(projection.rows.begin(), projection.rows.end());
        projection.rows.erase(std::unique(projection.rows.begin(), projection.rows.end()), projection.rows.end());
        projection.valid = true;
    }

public:
    BindingProvider(const CorrelateByBlueprint& bp, const search::fef::MatchDataLayout& layout, uint32_t docid_limit)
        : _match_data(layout.createMatchData()), _search(bp.create_correlate_search(*_match_data)),
          _docid_limit(docid_limit), _projections() {}

    std::span<const search::queryeval::BindingRow> read(uint32_t docid, std::span<const uint32_t> columns) override {
        if (!_search) return {};
        if (docid != _docid) read_document(docid);
        auto& projection = projection_for(columns);
        if (!projection.valid) project(projection);
        return projection.rows;
    }
};

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
        return std::make_unique<BindingProvider>(_blueprint, _layout, _docid_limit);
    }
};

bool reject_all_rows(uint32_t, std::span<const search::CorrelationBinding>) noexcept { return false; }

/** A bound value of a variable compared without case is already lowercase, so the grouped value
 *  must be lowercased too, or uppercase data in a case-insensitive field would never match. */
bool same_value(const search::queryeval::BindingValue& bound, const search::CorrelationValue& grouped, bool uncased) {
    if (uncased) {
        const auto* text = std::get_if<std::string_view>(&grouped);
        const auto* bound_text = std::get_if<std::string_view>(&bound);
        if (text != nullptr && bound_text != nullptr) return *bound_text == vespalib::LowerCase::convert(*text);
    }
    return bound == grouped;
}

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
        matchers.emplace_back([provider, projection = std::move(projection), uncased = std::move(uncased)]
                               (uint32_t docid, std::span<const search::CorrelationBinding> projected) {
            auto bindings = provider->read(docid, projection);
            return std::any_of(bindings.begin(), bindings.end(), [&](const auto& binding) {
                for (size_t i = 0; i < projected.size(); ++i)
                    if (!same_value(binding[i], projected[i].second, uncased[i])) return false;
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
