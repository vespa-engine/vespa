// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "position_document_field_node.h"

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/iteratorhandler.h>
#include <vespa/vespalib/geo/zcurve.h>

#include <algorithm>
#include <format>
#include <vector>

namespace search::expression {

using document::Document;
using document::DocumentType;
using document::FieldPath;
using document::PositionDataType;
using document::fieldvalue::IteratorHandler;
using vespalib::geo::ZCurve;

IMPLEMENT_EXPRESSIONNODE(PositionDocumentFieldNode, DocumentAccessorNode);

PositionDocumentFieldNode::PositionDocumentFieldNode() noexcept : _doc(nullptr) {
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
        _handler.reset();
    }
    return *this;
}

namespace {

/**
 * For complex field path access, if the last entry is array this returns true, else false.
 */
bool check_multivalue(const FieldPath& field_path) {
    if (field_path.empty()) {
        return false;
    }
    return field_path.back().getDataType().isArray();
}

class IntExtractor : public IteratorHandler {
public:
    int32_t value = 0;
    void onPrimitive(uint32_t, const Content& c) override { value = c.getValue().getAsInt(); }
};

class IntListExtractor : public IteratorHandler {
public:
    std::vector<int32_t> values;
    ~IntListExtractor() override;
    void onPrimitive(uint32_t, const Content& c) override { values.push_back(c.getValue().getAsInt()); }
};

IntListExtractor::~IntListExtractor() = default;

} // namespace

void PositionDocumentFieldNode::onDocType(const DocumentType& docType) {
    _x_path.clear();
    _y_path.clear();
    std::string x_name = std::format("{}.{}", _field_name, PositionDataType::FIELD_X);
    std::string y_name = std::format("{}.{}", _field_name, PositionDataType::FIELD_Y);
    docType.buildFieldPath(_x_path, x_name);
    docType.buildFieldPath(_y_path, y_name);

    FieldPath field_path;
    docType.buildFieldPath(field_path, _field_name);
    if (check_multivalue(field_path)) {
        _handler = std::make_unique<MultiValueHandler>();
    } else {
        _handler = std::make_unique<SingleValueHandler>();
    }
}

void PositionDocumentFieldNode::onDoc(const Document& doc) {
    _doc = &doc;
}

void PositionDocumentFieldNode::onPrepare(bool) {
    // Handler and result type are set up in onDocType
}

void PositionDocumentFieldNode::SingleValueHandler::handle(const Document& doc, const FieldPath& x_path,
                                                           const FieldPath& y_path) {
    IntExtractor x_ext;
    IntExtractor y_ext;
    doc.iterateNested(x_path.getFullRange(), x_ext);
    doc.iterateNested(y_path.getFullRange(), y_ext);
    _result.set(ZCurve::encode(x_ext.value, y_ext.value));
}

void PositionDocumentFieldNode::MultiValueHandler::handle(const Document& doc, const FieldPath& x_path,
                                                          const FieldPath& y_path) {
    IntListExtractor x_ext;
    IntListExtractor y_ext;
    doc.iterateNested(x_path.getFullRange(), x_ext);
    doc.iterateNested(y_path.getFullRange(), y_ext);

    size_t n = std::min(x_ext.values.size(), y_ext.values.size());
    _result.getVector().resize(n);
    for (size_t i = 0; i < n; ++i) {
        _result.getVector()[i].set(ZCurve::encode(x_ext.values[i], y_ext.values[i]));
    }
}

void PositionDocumentFieldNode::onExecute() const {
    if (_doc == nullptr || _x_path.empty() || _y_path.empty() || !_handler) {
        return;
    }
    _handler->handle(*_doc, _x_path, _y_path);
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
