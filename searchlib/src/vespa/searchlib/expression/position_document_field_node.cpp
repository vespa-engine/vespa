// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "position_document_field_node.h"

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/vespalib/geo/zcurve.h>

#include <format>

namespace search::expression {

using document::Document;
using document::DocumentType;
using document::FieldPathEntry;
using document::PositionDataType;
using document::fieldvalue::IteratorHandler;
using vespalib::geo::ZCurve;

IMPLEMENT_EXPRESSIONNODE(PositionDocumentFieldNode, DocumentAccessorNode);

PositionDocumentFieldNode::PositionDocumentFieldNode() noexcept
    : _doc(nullptr) {
}

PositionDocumentFieldNode::PositionDocumentFieldNode(const std::string& field_name)
    : _field_name(field_name), _doc(nullptr) {
}

PositionDocumentFieldNode::PositionDocumentFieldNode(const PositionDocumentFieldNode& rhs)
    : DocumentAccessorNode(rhs), _field_name(rhs._field_name), _doc(nullptr) {
}

PositionDocumentFieldNode::~PositionDocumentFieldNode() = default;

PositionDocumentFieldNode& PositionDocumentFieldNode::operator=(const PositionDocumentFieldNode& rhs) {
    if (this != &rhs) {
        DocumentAccessorNode::operator=(rhs);
        _field_name = rhs._field_name;
        _x_path.clear();
        _y_path.clear();
        _doc = nullptr;
    }
    return *this;
}

void PositionDocumentFieldNode::onDocType(const DocumentType& docType) {
    _x_path.clear();
    _y_path.clear();
    std::string x_name = std::format("{}.{}", _field_name, PositionDataType::FIELD_X);
    std::string y_name = std::format("{}.{}", _field_name, PositionDataType::FIELD_Y);
    docType.buildFieldPath(_x_path, x_name);
    docType.buildFieldPath(_y_path, y_name);
}

void PositionDocumentFieldNode::onDoc(const Document& doc) {
    _doc = &doc;
}

void PositionDocumentFieldNode::onPrepare(bool) {
    // Result type is always Int64
}

namespace {

class IntExtractor : public IteratorHandler {
public:
    int32_t value = 0;
    void onPrimitive(uint32_t, const Content& c) override {
        value = c.getValue().getAsInt();
    }
};

}

void PositionDocumentFieldNode::onExecute() const {
    if (_doc == nullptr || _x_path.empty() || _y_path.empty()) {
        _result.set(0);
        return;
    }

    int32_t x = 0;
    int32_t y = 0;

    IntExtractor x_extractor;
    _doc->iterateNested(_x_path.getFullRange(), x_extractor);
    x = x_extractor.value;

    IntExtractor y_extractor;
    _doc->iterateNested(_y_path.getFullRange(), y_extractor);
    y = y_extractor.value;

    int64_t zcurve = ZCurve::encode(x, y);
    _result.set(zcurve);
}

vespalib::Serializer& PositionDocumentFieldNode::onSerialize(vespalib::Serializer& os) const {
    return os << _field_name;
}

vespalib::Deserializer& PositionDocumentFieldNode::onDeserialize(vespalib::Deserializer& is) {
    return is >> _field_name;
}

void PositionDocumentFieldNode::visitMembers(vespalib::ObjectVisitor& visitor) const {
    visit(visitor, "fieldName", _field_name);
}

} // namespace search::expression
