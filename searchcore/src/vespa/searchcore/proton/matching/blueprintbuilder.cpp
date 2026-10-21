// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprintbuilder.h"

#include "querynodes.h"

#include <vespa/check_require.h>
#include <vespa/searchcorespi/index/indexsearchable.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/query/tree/customtypevisitor.h>
#include <vespa/searchlib/queryeval/create_blueprint_params.h>
#include <vespa/searchlib/queryeval/correlate_by_blueprint.h>
#include <vespa/searchlib/queryeval/equiv_blueprint.h>
#include <vespa/searchlib/queryeval/get_weight_from_node.h>
#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/searchlib/queryeval/label_wrapper_blueprint.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/same_element_blueprint.h>
#include <vespa/vespalib/util/issue.h>

#include <map>
#include <optional>
#include <set>

using namespace search::queryeval;
using search::query::Node;

namespace proton::matching {

namespace {

struct Mixer {
    std::unique_ptr<OrBlueprint> attributes;

    Mixer() noexcept : attributes() {}

    void addAttribute(Blueprint::UP attr) {
        if (!attributes) {
            attributes = std::make_unique<OrBlueprint>();
        }
        attributes->addChild(std::move(attr));
    }

    Blueprint::UP mix(Blueprint::UP indexes) {
        if (!attributes) {
            if (!indexes) {
                return std::make_unique<EmptyBlueprint>();
            }
            return indexes;
        }
        if (!indexes) {
            if (attributes->childCnt() == 1) {
                return attributes->removeChild(0);
            } else {
                return std::move(attributes);
            }
        }
        attributes->addChild(std::move(indexes));
        return std::move(attributes);
    }
};

/**
 * requires that match data space has been reserved
 */
class BlueprintBuilderVisitor : public search::query::CustomTypeVisitor<ProtonNodeTypes> {
private:
    const IRequestContext&        _requestContext;
    ISearchContext&               _context;
    search::fef::MatchDataLayout& _global_layout;
    Blueprint::UP                 _result;

    void buildChildren(IntermediateBlueprint& parent, const std::vector<Node*>& children);
    bool is_search_multi_threaded() const noexcept { return _requestContext.thread_bundle().size() > 1; }

    template <typename NodeType>
    void buildIntermediate(IntermediateBlueprint* b, NodeType& n) __attribute__((noinline));

    void buildWeakAnd(ProtonWeakAnd& n) {
        auto*         wand = new WeakAndBlueprint(n.getTargetNumHits(),
                                                  _requestContext.get_create_blueprint_params().weakand_stop_word_strategy,
                                                  is_search_multi_threaded());
        Blueprint::UP result(wand);
        for (auto node : n.getChildren()) {
            uint32_t weight = getWeightFromNode(*node).percent();
            wand->addTerm(build(_requestContext, *node, _context, _global_layout), weight);
        }
        _result = std::move(result);
    }

    void buildEquiv(ProtonEquiv& n) {
        double            eqw = n.getWeight().percent();
        FieldSpecBaseList specs;
        specs.reserve(n.numFields());
        for (size_t i = 0; i < n.numFields(); ++i) {
            specs.add(n.field(i).fieldSpec());
        }
        auto eq = std::make_unique<EquivBlueprint>(std::move(specs), EquivBlueprint::allocate_outside_equiv_tag{});
        for (auto node : n.getChildren()) {
            double w = getWeightFromNode(*node).percent();
            eq->addTerm(build(_requestContext, *node, _context, _global_layout), w / eqw);
        }
        eq->setDocIdLimit(_context.getDocIdLimit());
        n.setDocumentFrequency(eq->getState().estimate().estHits, _context.getDocIdLimit());
        _result = std::move(eq);
    }

    void buildWordAlternatives(ProtonWordAlternatives& n) {
        const auto& children = n.getChildren();
        CHECK(children.size() == n.getNumTerms());
        double                                  eqw = n.getWeight().percent();
        std::vector<std::unique_ptr<Blueprint>> term_bps;
        for (const auto& child : children) {
            term_bps.emplace_back(build(_requestContext, *child, _context, _global_layout));
        }
        FieldSpecBaseList specs;
        specs.reserve(n.numFields());
        for (size_t i = 0; i < n.numFields(); ++i) {
            specs.add(n.field(i).fieldSpec());
        }
        auto eq = std::make_unique<EquivBlueprint>(std::move(specs), EquivBlueprint::allocate_outside_equiv_tag{});
        CHECK(term_bps.size() == n.getNumTerms());
        for (uint32_t idx = 0; idx < n.getNumTerms(); idx++) {
            const auto& child = children[idx];
            double      w = child->getWeight().percent();
            eq->addTerm(std::move(term_bps[idx]), w / eqw);
        }
        eq->setDocIdLimit(_context.getDocIdLimit());
        n.setDocumentFrequency(eq->getState().estimate().estHits, _context.getDocIdLimit());
        _result = std::move(eq);
    }

    void buildLabelWrapper(ProtonLabelWrapper& n) {
        // The reserved "no field" is resolved unconditionally, so this should always hold;
        // fall back to a plain rank if it somehow does not, losing only the raw score.
        if (n.numFields() != 1) {
            buildIntermediate(new RankBlueprint(), n);
            return;
        }
        auto handle = n.field(0).getHandle(); // Records in HandleRecorder
        if (handle == search::fef::IllegalHandle) {
            buildIntermediate(new RankBlueprint(), n);
            return;
        }
        buildIntermediate(new LabelWrapperBlueprint(handle, n.getLabelScore()), n);
    }

    void buildSameElement(ProtonSameElement& n) {
        if (n.numFields() == 1) {
            auto se = std::make_unique<SameElementBlueprint>(n.field(0).fieldSpec(), n.descendants_index_handles,
                                                             n.is_expensive(), n.expose_match_data_for_same_element,
                                                             n.get_element_filter()); // Copy element filter
            for (auto* node : n.getChildren()) {
                se->addChild(build(_requestContext, *node, _context, _global_layout));
            }
            _result = std::move(se);
        } else {
            // numFields() == 0 happens if multiple schemas are queried and the current schema does not contain the
            // searched field. Produce a more meaningful error message in this case.
            std::string document_type_name(_context.get_document_type_name());
            if (n.numFields() == 0) {
                vespalib::Issue::report(
                    "SameElement operator does not search in a field. Is '%s' a field of document type '%s'?",
                    n.getView().c_str(), document_type_name.c_str());
            } else if (n.numFields() > 1) {
                vespalib::Issue::report("SameElement operator searches in more than one field: '%s' maps to "
                                        "%zu fields of document type '%s'.",
                                        n.getView().c_str(), n.numFields(), document_type_name.c_str());
            }
            _result = std::make_unique<EmptyBlueprint>();
        }
    }

    // Reports why a correlateBy query cannot run and makes it match nothing.
    void reject_correlate_by(const std::string& message) {
        vespalib::Issue::report("correlateBy: %s", message.c_str());
        _result = std::make_unique<EmptyBlueprint>();
    }

    // Checks the clause structure that the correlation search relies on.
    bool check_correlate_by_clauses(const ProtonCorrelateBy& n) {
        const auto& bindings = n.bindings();
        if (bindings.size() != n.getChildren().size() || n.exact_values().size() != bindings.size()) {
            reject_correlate_by("each clause needs its own bindings and exact values");
            return false;
        }
        for (const auto& clause : bindings) {
            if (clause.empty()) {
                reject_correlate_by("each clause must bind at least one variable");
                return false;
            }
        }
        std::set<uint32_t> presence_clauses;
        std::set<uint32_t> matching_clauses;
        for (const auto& selection : n.first_present()) {
            if (selection.clause >= bindings.size() || selection.presence >= bindings.size() ||
                selection.clause == selection.presence) {
                reject_correlate_by("firstPresent refers to clauses that do not exist");
                return false;
            }
            if (!presence_clauses.insert(selection.presence).second || !matching_clauses.insert(selection.clause).second) {
                reject_correlate_by("each firstPresent needs its own matching and presence clause");
                return false;
            }
            if (bindings[selection.clause] != bindings[selection.presence] ||
                !bindings[selection.clause].contains(selection.variable)) {
                reject_correlate_by("firstPresent clauses must bind the same fields, including variable '" +
                                    selection.variable + "'");
                return false;
            }
        }
        for (uint32_t clause : matching_clauses) {
            if (presence_clauses.contains(clause)) {
                reject_correlate_by("a firstPresent presence clause cannot also be a matching clause");
                return false;
            }
        }
        if (presence_clauses.size() == bindings.size()) {
            reject_correlate_by("at least one clause must be a matching clause");
            return false;
        }
        return true;
    }

    void buildCorrelateBy(ProtonCorrelateBy& n) {
        if (!check_correlate_by_clauses(n)) {
            return;
        }
        std::string document_type(_context.get_document_type_name());
        std::map<std::string, uint32_t> columns;
        for (const auto& bindings : n.bindings()) {
            for (const auto& [variable, field] : bindings) columns.emplace(variable, 0);
        }
        uint32_t column_id = 0;
        for (auto& [variable, id] : columns) id = column_id++;
        std::vector<std::optional<std::vector<std::string>>> domains(columns.size());
        for (const auto& [variable, values] : n.domains()) {
            auto column = columns.find(variable);
            if (column == columns.end()) {
                reject_correlate_by("the domain of variable '" + variable + "' refers to a variable no clause binds");
                return;
            }
            domains[column->second] = values;
        }
        auto bp = std::make_unique<CorrelateByBlueprint>(std::move(domains), _requestContext.getDoom());
        bp->set_correlation_identity(n.label(), columns);
        std::map<std::string, bool> string_variables;
        for (size_t i = 0; i < n.getChildren().size(); ++i) {
            auto* clause = dynamic_cast<ProtonSameElement*>(n.getChildren()[i]);
            if (!clause) {
                reject_correlate_by("every clause must be a sameElement");
                return;
            }
            std::vector<BoundAttribute> attributes;
            for (const auto& [variable, field] : n.bindings()[i]) {
                std::string name = clause->getView() + "." + field;
                auto* attr = _requestContext.getAttributeStableEnum(name);
                if (!attr) {
                    reject_correlate_by("variable '" + variable + "' is bound to '" + name +
                                        "', which is not an attribute in document type '" + document_type + "'");
                    return;
                }
                if (attr->getCollectionType() != search::attribute::CollectionType::ARRAY ||
                    (!attr->isStringType() && !attr->isIntegerType())) {
                    reject_correlate_by("variable '" + variable + "' requires '" + name +
                                        "' to be an integer or string array attribute");
                    return;
                }
                auto [entry, inserted] = string_variables.emplace(variable, attr->isStringType());
                if (!inserted && entry->second != attr->isStringType()) {
                    reject_correlate_by("variable '" + variable + "' is bound to both integer and string attributes");
                    return;
                }
                if (n.domains().contains(variable) && !attr->isStringType()) {
                    reject_correlate_by("the domain of variable '" + variable + "' requires string attributes");
                    return;
                }
                attributes.push_back({columns.at(variable), attr});
            }
            std::vector<search::queryeval::ExactAttributeValues> exact_values;
            for (const auto& [field, values] : n.exact_values()[i]) {
                std::string name = clause->getView() + "." + field;
                auto* attr = _requestContext.getAttributeStableEnum(name);
                if (!attr || !attr->isStringType() || attr->getCollectionType() != search::attribute::CollectionType::ARRAY) {
                    reject_correlate_by("exact values require '" + name + "' to be a string array attribute in document type '" +
                                        document_type + "'");
                    return;
                }
                exact_values.push_back({attr, values});
            }
            bp->add_clause(build(_requestContext, *clause, _context, _global_layout), std::move(attributes), std::move(exact_values));
        }
        for (const auto& selection : n.first_present()) {
            auto variable = string_variables.find(selection.variable);
            if (variable == string_variables.end() || !variable->second) {
                reject_correlate_by("firstPresent requires a string priority variable '" + selection.variable + "'");
                return;
            }
            bp->add_first_present(selection.clause, selection.presence, columns.at(selection.variable), selection.values);
        }
        _result = std::move(bp);
    }

    template <typename NodeType> void buildTerm(NodeType& n) {
        FieldSpecList indexFields;
        Mixer         mixer;
        for (size_t i = 0; i < n.numFields(); ++i) {
            const ProtonTermData::FieldEntry& field = n.field(i);
            CHECK(field.getFieldId() != search::fef::IllegalFieldId);
            CHECK(field.getHandle() != search::fef::IllegalHandle);
            if (field.attribute_field) {
                mixer.addAttribute(
                    _context.getAttributes().createBlueprint(_requestContext, field.fieldSpec(), n, _global_layout));
            } else {
                indexFields.add(field.fieldSpec());
            }
        }
        Blueprint::UP indexBlueprint;
        if (!indexFields.empty()) {
            indexBlueprint = _context.getIndexes().createBlueprint(_requestContext, indexFields, n, _global_layout);
        }
        _result = mixer.mix(std::move(indexBlueprint));
        _result->setDocIdLimit(_context.getDocIdLimit());
        n.setDocumentFrequency(_result->getState().estimate().estHits, _context.getDocIdLimit());
    }

protected:
    void visit(ProtonAnd& n) override { buildIntermediate(new AndBlueprint(), n); }
    void visit(ProtonAndNot& n) override { buildIntermediate(new AndNotBlueprint(n.elementwise), n); }
    void visit(ProtonOr& n) override { buildIntermediate(new OrBlueprint(), n); }
    void visit(ProtonWeakAnd& n) override { buildWeakAnd(n); }
    void visit(ProtonEquiv& n) override { buildEquiv(n); }
    void visit(ProtonWordAlternatives& n) override { buildWordAlternatives(n); }
    void visit(ProtonRank& n) override { buildIntermediate(new RankBlueprint(), n); }
    void visit(ProtonLabelWrapper& n) override { buildLabelWrapper(n); }
    void visit(ProtonNear& n) override {
        buildIntermediate(new NearBlueprint(n.getDistance(), n.num_negative_terms(), n.exclusion_distance(),
                                            _requestContext.get_element_gap_inspector()),
                          n);
    }
    void visit(ProtonONear& n) override {
        buildIntermediate(new ONearBlueprint(n.getDistance(), n.num_negative_terms(), n.exclusion_distance(),
                                             _requestContext.get_element_gap_inspector()),
                          n);
    }
    void visit(ProtonSameElement& n) override { buildSameElement(n); }
    void visit(ProtonCorrelateBy& n) override { buildCorrelateBy(n); }

    void visit(ProtonWeightedSetTerm& n) override { buildTerm(n); }
    void visit(ProtonDotProduct& n) override { buildTerm(n); }
    void visit(ProtonWandTerm& n) override { buildTerm(n); }

    void visit(ProtonPhrase& n) override { buildTerm(n); }
    void visit(ProtonNumberTerm& n) override { buildTerm(n); }
    void visit(ProtonLocationTerm& n) override { buildTerm(n); }
    void visit(ProtonPrefixTerm& n) override { buildTerm(n); }
    void visit(ProtonRangeTerm& n) override { buildTerm(n); }
    void visit(ProtonStringRangeTerm& n) override { buildTerm(n); }
    void visit(ProtonStringTerm& n) override { buildTerm(n); }
    void visit(ProtonSubstringTerm& n) override { buildTerm(n); }
    void visit(ProtonSuffixTerm& n) override { buildTerm(n); }
    void visit(ProtonPredicateQuery& n) override { buildTerm(n); }
    void visit(ProtonRegExpTerm& n) override { buildTerm(n); }
    void visit(ProtonNearestNeighborTerm& n) override { buildTerm(n); }
    void visit(ProtonTrue&) override { _result = std::make_unique<AlwaysTrueBlueprint>(); }
    void visit(ProtonFalse&) override { _result = std::make_unique<EmptyBlueprint>(); }
    void visit(ProtonFuzzyTerm& n) override { buildTerm(n); }
    void visit(ProtonInTerm& n) override { buildTerm(n); }

public:
    BlueprintBuilderVisitor(const IRequestContext& requestContext, ISearchContext& context,
                            search::fef::MatchDataLayout& global_layout)
        : _requestContext(requestContext), _context(context), _global_layout(global_layout), _result() {}
    Blueprint::UP build() {
        CHECK(_result);
        return std::move(_result);
    }
    static Blueprint::UP build(const IRequestContext& requestContext, Node& node, ISearchContext& context,
                               search::fef::MatchDataLayout& global_layout) {
        CreateBlueprintProfilerGuard profiler_guard;
        BlueprintBuilderVisitor      visitor(requestContext, context, global_layout);
        node.accept(visitor);
        Blueprint::UP result = visitor.build();
        profiler_guard.set_name_with(*result);
        return result;
    }
};

void BlueprintBuilderVisitor::buildChildren(IntermediateBlueprint& parent, const std::vector<Node*>& children) {
    parent.reserve(children.size());
    for (auto child : children) {
        parent.addChild(build(_requestContext, *child, _context, _global_layout));
    }
}

template <typename NodeType> void BlueprintBuilderVisitor::buildIntermediate(IntermediateBlueprint* b, NodeType& n) {
    std::unique_ptr<IntermediateBlueprint> blueprint(b);
    buildChildren(*blueprint, n.getChildren());
    _result = std::move(blueprint);
}

IntermediateBlueprint* asRankOrAndNot(Blueprint* blueprint) {
    return ((blueprint->isAndNot() || blueprint->isRank())) ? blueprint->asIntermediate() : nullptr;
}

IntermediateBlueprint* lastConsequtiveRankOrAndNot(Blueprint* blueprint) {
    IntermediateBlueprint* prev = nullptr;
    IntermediateBlueprint* curr = asRankOrAndNot(blueprint);
    while (curr != nullptr) {
        prev = curr;
        curr = asRankOrAndNot(&curr->getChild(0));
    }
    return prev;
}

} // namespace

Blueprint::UP BlueprintBuilder::build(const IRequestContext& requestContext, Node& node, Blueprint::UP whiteList,
                                      ISearchContext& context, MatchDataLayout& global_layout) {
    auto blueprint = BlueprintBuilderVisitor::build(requestContext, node, context, global_layout);
    if (whiteList) {
        auto                   andBlueprint = std::make_unique<AndBlueprint>();
        IntermediateBlueprint* rankOrAndNot = lastConsequtiveRankOrAndNot(blueprint.get());
        if (rankOrAndNot != nullptr) {
            (*andBlueprint).addChild(rankOrAndNot->removeChild(0)).addChild(std::move(whiteList));
            rankOrAndNot->insertChild(0, std::move(andBlueprint));
        } else {
            (*andBlueprint).addChild(std::move(blueprint)).addChild(std::move(whiteList));
            blueprint = std::move(andBlueprint);
        }
    }
    blueprint->setDocIdLimit(context.getDocIdLimit());
    return blueprint;
}

} // namespace proton::matching
