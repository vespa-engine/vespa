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
    uint32_t           _inspecting_ancestor_nodes;
    IIndexEnvironment& _index_env;

    void visit_handles(const ProtonTermData& n);

    bool needs_normal_features() const noexcept { return _inspecting_ancestor_nodes != 0u; }

    void maybe_visit_handles(ProtonTermData& n) {
        if (needs_normal_features()) {
            visit_handles(n);
        }
    }
public:
    TagNeededHandlesVisitor(search::fef::IIndexEnvironment& index_env);
    ~TagNeededHandlesVisitor() override;

    template <class TermNode>
    void visitTerm(TermNode& n) { maybe_visit_handles(n); }

    void visit(ProtonNodeTypes::Equiv& n) override;
    void visit(ProtonNodeTypes::Near& n) override;
    void visit(ProtonNodeTypes::ONear& n) override;
    /*
     * ProtonNodeTypes::Phrase and ProtonNodeTypes::SameElement is handled as term by superclass and descendants
     * unpack features to a separate match data instance.
     */
};

TagNeededHandlesVisitor::TagNeededHandlesVisitor(IIndexEnvironment& index_env)
    : TemplateTermVisitor<TagNeededHandlesVisitor, ProtonNodeTypes>(),
      _inspecting_ancestor_nodes(0u),
      _index_env(index_env)
{
}

TagNeededHandlesVisitor::~TagNeededHandlesVisitor() = default;

void
TagNeededHandlesVisitor::visit_handles(const ProtonTermData& n)
{
    auto num_fields = n.numFields();
    for (uint32_t i = 0; i < num_fields; ++i) {
        auto& tfd = n.field(i);
        auto field_id = tfd.getFieldId();
        auto* field_info = _index_env.getField(field_id);
        if (field_info != nullptr && field_info->type() == FieldType::INDEX) {
            auto handle = tfd.getHandle(); // Records in HandleRecorder
            (void) handle;
        }
    }
}

void
TagNeededHandlesVisitor::visit(ProtonNodeTypes::Equiv& n)
{
    maybe_visit_handles(n);
    // Descendants unpack features to a separate match data instance.
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
tag_needed_handles(HandleRecorder& handle_recorder, IIndexEnvironment& index_env,
                        search::query::Node& node)
{
    TagNeededHandlesVisitor visitor(index_env);
    HandleRecorder::Binder binder(handle_recorder);
    node.accept(visitor);
}

}
