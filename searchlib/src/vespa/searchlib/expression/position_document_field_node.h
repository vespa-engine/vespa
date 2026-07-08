// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentaccessornode.h"
#include "integerresultnode.h"
#include "resultvector.h"

#include <vespa/document/base/fieldpath.h>

#include <memory>
#include <string>

namespace search::expression {

/**
 * Position field access for streaming search.
 *
 * Reads pos.x and pos.y from the document and encodes as zcurve integer(s).
 */
class PositionDocumentFieldNode : public DocumentAccessorNode {
    std::string               _field_name;
    document::FieldPath       _x_path;
    document::FieldPath       _y_path;
    const document::Document* _doc;

    class Handler {
    public:
        virtual ~Handler() = default;
        virtual void handle(const document::Document& doc, const document::FieldPath& x_path,
                            const document::FieldPath& y_path) = 0;
        [[nodiscard]] virtual const ResultNode* result() const noexcept = 0;
    };

    /**
     * For single value result. Reads pos.x and pos.y and computes a single zcurve value.
     */
    class SingleValueHandler : public Handler {
        mutable Int64ResultNode _result;

    public:
        void handle(const document::Document& doc, const document::FieldPath& x_path,
                    const document::FieldPath& y_path) override;
        [[nodiscard]] const ResultNode* result() const noexcept override { return &_result; }
    };

    /**
     * For multi-value result. Reads pos.x and pos.y for each pos in the array field in the document.
     */
    class MultiValueHandler : public Handler {
        mutable IntegerResultNodeVector _result;

    public:
        void handle(const document::Document& doc, const document::FieldPath& x_path,
                    const document::FieldPath& y_path) override;
        [[nodiscard]] const ResultNode* result() const noexcept override { return &_result; }
    };

    std::unique_ptr<Handler> _handler;

public:
    DECLARE_EXPRESSIONNODE(PositionDocumentFieldNode);

    PositionDocumentFieldNode() noexcept;
    explicit PositionDocumentFieldNode(const std::string& field_name);
    PositionDocumentFieldNode(const PositionDocumentFieldNode&);
    ~PositionDocumentFieldNode() override;
    PositionDocumentFieldNode& operator=(const PositionDocumentFieldNode&);

    // DocumentAccessorNode
    [[nodiscard]] const std::string& getFieldName() const override { return _field_name; }

    // Identifiable
    void visitMembers(vespalib::ObjectVisitor& visitor) const override;

private:
    // DocumentAccessorNode
    void onDocType(const document::DocumentType& docType) override;
    void onDoc(const document::Document& doc) override;

    // ExpressionNode
    void onPrepare(bool preserveAccurateTypes) override;
    void onExecute() const override;
    [[nodiscard]] const ResultNode* getResult() const override { return _handler ? _handler->result() : nullptr; }

    // Identifiable
    vespalib::Serializer& onSerialize(vespalib::Serializer& os) const override;
    vespalib::Deserializer& onDeserialize(vespalib::Deserializer& is) override;
};

} // namespace search::expression
