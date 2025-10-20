// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tag_needed_handles.h"
#include <vespa/searchcore/proton/matching/handlerecorder.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/query/tree/templatetermvisitor.h>

using search::fef::FieldType;
using search::fef::IIndexEnvironment;
using search::query::TemplateTermVisitor;

namespace proton::matching {

class TagNeededHandlesVisitor : public TemplateTermVisitor<TagNeededHandlesVisitor, ProtonNodeTypes>
{
    uint32_t                 _inspecting_ancestor_nodes;
    uint32_t                 _changed_match_data;
    const IIndexEnvironment& _index_env;

    void visit_field_specs(ProtonTermData& n);

    bool needs_normal_features() const noexcept { return _inspecting_ancestor_nodes != 0u; }
    bool original_match_data() const noexcept { return _changed_match_data == 0u; }

    void maybe_visit_field_specs(ProtonTermData& n) {
        if (needs_normal_features()) {
            visit_field_specs(n);
        }
    }
public:
    TagNeededHandlesVisitor(const search::fef::IIndexEnvironment& index_env);
    ~TagNeededHandlesVisitor() override;

    template <class TermNode>
    void visitTerm(TermNode& n) { maybe_visit_field_specs(n); }

    void visit(ProtonNodeTypes::Equiv& n) override;
    void visit(ProtonNodeTypes::WordAlternatives& n) override;
    void visit(ProtonNodeTypes::Near& n) override;
    void visit(ProtonNodeTypes::ONear& n) override;
    void visit(ProtonNodeTypes::Phrase& n) override;
    void visit(ProtonNodeTypes::SameElement& n) override;
};

TagNeededHandlesVisitor::TagNeededHandlesVisitor(const IIndexEnvironment& index_env)
    : TemplateTermVisitor<TagNeededHandlesVisitor, ProtonNodeTypes>(),
      _inspecting_ancestor_nodes(0u),
      _changed_match_data(0u),
      _index_env(index_env)
{
}

TagNeededHandlesVisitor::~TagNeededHandlesVisitor() = default;

void
TagNeededHandlesVisitor::visit_field_specs(ProtonTermData& n)
{
    auto num_fields = n.numFields();
    for (uint32_t i = 0; i < num_fields; ++i) {
        auto& tfd = n.field(i);
        auto field_id = tfd.getFieldId();
        auto* field_info = _index_env.getField(field_id);
        if (field_info != nullptr && field_info->type() == FieldType::INDEX) {
            if (original_match_data()) {
                auto handle = tfd.getHandle(); // Records in HandleRecorder
                (void) handle;
            }
            /*
             * Unpack of normal features is needed for query recall. Ignore rank: filter, filter threshold from schema
             * and rank profile and _position_data flag in query term.
             */
            tfd.disable_filter();
        }
    }
}

void
TagNeededHandlesVisitor::visit(ProtonNodeTypes::Equiv& n)
{
    maybe_visit_field_specs(n);
    visitChildren(n);
}

void
TagNeededHandlesVisitor::visit(ProtonNodeTypes::WordAlternatives& n)
{
    maybe_visit_field_specs(n);
}

void
TagNeededHandlesVisitor::visit(ProtonNodeTypes::Near& n)
{
    ++_inspecting_ancestor_nodes;
    visitChildren(n);
    --_inspecting_ancestor_nodes;
}

void
TagNeededHandlesVisitor::visit(ProtonNodeTypes::ONear& n)
{
    ++_inspecting_ancestor_nodes;
    visitChildren(n);
    --_inspecting_ancestor_nodes;
}

void
TagNeededHandlesVisitor::visit(ProtonNodeTypes::Phrase& n)
{
    maybe_visit_field_specs(n);
    ++_inspecting_ancestor_nodes;
    ++_changed_match_data;
    visitChildren(n);
    --_changed_match_data;
    --_inspecting_ancestor_nodes;
}

void
TagNeededHandlesVisitor::visit(ProtonNodeTypes::SameElement& n)
{
    ++_inspecting_ancestor_nodes;
    visitChildren(n);
    --_inspecting_ancestor_nodes;
}

void
tag_needed_handles(search::query::Node& node, HandleRecorder& handle_recorder, const IIndexEnvironment& index_env)
{
    TagNeededHandlesVisitor visitor(index_env);
    HandleRecorder::Binder binder(handle_recorder);
    node.accept(visitor);
}

}
