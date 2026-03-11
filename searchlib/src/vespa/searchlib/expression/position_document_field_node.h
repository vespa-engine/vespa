// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentaccessornode.h"
#include "integerresultnode.h"

#include <vespa/document/base/fieldpath.h>

#include <string>

namespace search::expression {

/**
 * Position field access for streaming search.
 *
 * Reads pos.x and pos.y from the document and encodes as zcurve integer.
 */
class PositionDocumentFieldNode : public DocumentAccessorNode
{
    std::string                  _field_name;
    document::FieldPath          _x_path;
    document::FieldPath          _y_path;
    const document::Document*    _doc;
    mutable Int64ResultNode      _result;

public:
    DECLARE_EXPRESSIONNODE(PositionDocumentFieldNode);

    PositionDocumentFieldNode() noexcept;
    explicit PositionDocumentFieldNode(const std::string& field_name);
    PositionDocumentFieldNode(const PositionDocumentFieldNode&);
    ~PositionDocumentFieldNode() override;
    PositionDocumentFieldNode& operator=(const PositionDocumentFieldNode&);

    // DocumentAccessorNode
    const std::string& getFieldName() const override { return _field_name; }

    // Identifiable
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;

private:
    // DocumentAccessorNode
    void onDocType(const document::DocumentType& docType) override;
    void onDoc(const document::Document& doc) override;

    // ExpressionNode
    void onPrepare(bool preserveAccurateTypes) override;
    void onExecute() const override;
    const ResultNode* getResult() const override { return &_result; }

    // Identifiable
    vespalib::Serializer& onSerialize(vespalib::Serializer& os) const override;
    vespalib::Deserializer& onDeserialize(vespalib::Deserializer& is) override;
};

}
