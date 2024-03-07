// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentfieldnode.h"

namespace search::expression {

/*
 * Interpolated lookup for streaming search.
 */
class InterpolatedDocumentFieldLookupNode : public DocumentFieldNode
{
public:
    DECLARE_EXPRESSIONNODE(InterpolatedDocumentFieldLookupNode);
    DECLARE_NBO_SERIALIZE;

    InterpolatedDocumentFieldLookupNode() noexcept;
    InterpolatedDocumentFieldLookupNode(vespalib::stringref name, std::unique_ptr<ExpressionNode> arg);
    InterpolatedDocumentFieldLookupNode(const InterpolatedDocumentFieldLookupNode& rhs);
    ~InterpolatedDocumentFieldLookupNode() override;
    InterpolatedDocumentFieldLookupNode& operator=(const InterpolatedDocumentFieldLookupNode &rhs);
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;
    void selectMembers(const vespalib::ObjectPredicate& predicate, vespalib::ObjectOperation& operation) override;
private:
    void onPrepare(bool preserveAccurateTypes) override;
    bool onExecute() const override;
    vespalib::IdentifiablePtr<ExpressionNode>  _lookup_expression;
    mutable std::vector<double>                _values;
    mutable FloatResultNode                    _float_result;
};

}
