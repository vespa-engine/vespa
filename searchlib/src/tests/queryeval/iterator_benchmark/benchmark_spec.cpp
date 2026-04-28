// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "benchmark_spec.h"

#include <vespa/searchlib/queryeval/intermediate_blueprints.h>
#include <vespa/vespalib/util/overload.h>

#include <ostream>
#include <sstream>

using vespalib::overload;

namespace search::queryeval::test {

Spec term(FieldConfig field, double hit_ratio) {
    return Spec{TermSpec{std::move(field), hit_ratio}};
}

std::ostream& operator<<(std::ostream& os, const Spec& s) {
    auto print_intermediate = [&](const char* name, const std::vector<Spec>& children) {
        os << name << "[";
        for (size_t i = 0; i < children.size(); ++i) {
            if (i) {
                os << ", ";
            }
            os << children[i];
        }
        os << "]";
    };

    std::visit(overload{
        [&](const TermSpec& t) {
            os << "Term(field=" << t.field.to_string() << ", r=" << t.hit_ratio << ")";
        },
        [&](const AndSpec& a) { print_intermediate("And", a.children); },
        [&](const OrSpec& o)  { print_intermediate("Or",  o.children); },
    }, s.node);
    return os;
}

std::string to_string(const Spec& s) {
    std::stringstream os;
    os << s;
    return os.str();
}

SpecBlueprintFactory::SpecBlueprintFactory(Spec spec, uint32_t num_docs)
        : _spec(std::move(spec)),
          _num_docs(num_docs),
          _leaf_factories()
{
    collect_leaves(_spec, _num_docs, _leaf_factories);
}


SpecBlueprintFactory::~SpecBlueprintFactory() {

}

void SpecBlueprintFactory::collect_leaves(const Spec& spec, uint32_t num_docs, FactoryList& out) {
    std::visit(overload{
        [&](const TermSpec& t) {
            out.push_back(make_blueprint_factory(
                t.field, QueryOperator::Term, num_docs,
                /*default_values_per_document*/ 0,
                t.hit_ratio,
                /*children*/ 1,
                /*disjunct_children*/ false));
        },
        [&](const AndSpec& a) {
            for (const auto& c : a.children) { collect_leaves(c, num_docs, out); }
        },
        [&](const OrSpec& o) {
            for (const auto& c : o.children) { collect_leaves(c, num_docs, out); }
        },
    }, spec.node);
}

std::unique_ptr<Blueprint> SpecBlueprintFactory::build_tree(const Spec& spec, FactoryList& leaves, size_t& leaf_idx,
                                                 uint32_t docid_limit) {
    return std::visit(overload{
        [&](const TermSpec&) -> std::unique_ptr<Blueprint> {
            return leaves[leaf_idx++]->make_blueprint();
        },
        [&](const AndSpec& a) -> std::unique_ptr<Blueprint> {
            auto bp = std::make_unique<AndBlueprint>();
            for (const auto& c : a.children) {
                bp->addChild(build_tree(c, leaves, leaf_idx, docid_limit));
            }
            bp->setDocIdLimit(docid_limit);
            bp->update_flow_stats(docid_limit);
            return bp;
        },
        [&](const OrSpec& o) -> std::unique_ptr<Blueprint> {
            auto bp = std::make_unique<OrBlueprint>();
            for (const auto& c : o.children) {
                bp->addChild(build_tree(c, leaves, leaf_idx, docid_limit));
            }
            bp->setDocIdLimit(docid_limit);
            bp->update_flow_stats(docid_limit);
            return bp;
        },
    }, spec.node);
}

std::unique_ptr<Blueprint> SpecBlueprintFactory::make_blueprint() {
    size_t leaf_idx = 0;
    return build_tree(_spec, _leaf_factories, leaf_idx, _num_docs + 1);
}

std::string SpecBlueprintFactory::get_name(Blueprint&) const {
    return to_string(_spec);
}

}
