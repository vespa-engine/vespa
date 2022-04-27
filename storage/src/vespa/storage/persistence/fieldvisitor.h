// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#pragma once

#include <vespa/document/select/node.h>
#include <vespa/document/select/valuenode.h>
#include <vespa/document/select/visitor.h>
#include <vespa/document/select/branch.h>
#include <vespa/document/select/compare.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/datatype/documenttype.h>

namespace storage {

class FieldVisitor : public document::select::Visitor {
private:
    const document::DocumentType & _docType;
    document::Field::Set::Builder _fields;
    
public:
    explicit FieldVisitor(const document::DocumentType & docType)
        : _docType(docType),
          _fields()
    {}
    ~FieldVisitor() override;

    document::FieldCollection getFieldSet() {
        return document::FieldCollection(_docType, _fields.build());
    }

    void visitFieldValueNode(const document::select::FieldValueNode &) override;
    void visitComparison(const document::select::Compare &) override; 
    void visitAndBranch(const document::select::And &) override;
    void visitOrBranch(const document::select::Or &) override;
    void visitNotBranch(const document::select::Not &) override;

    // Ignored node types 
    void visitConstant(const document::select::Constant &) override {}
    void visitInvalidConstant(const document::select::InvalidConstant &) override {}
    void visitDocumentType(const document::select::DocType &) override {}
    void visitArithmeticValueNode(const document::select::ArithmeticValueNode &) override {}
    void visitFunctionValueNode(const document::select::FunctionValueNode &) override {}
    void visitIdValueNode(const document::select::IdValueNode &) override {}
    void visitFloatValueNode(const document::select::FloatValueNode &) override {}
    void visitVariableValueNode(const document::select::VariableValueNode &) override {}
    void visitIntegerValueNode(const document::select::IntegerValueNode &) override {}
    void visitBoolValueNode(const document::select::BoolValueNode &) override {}
    void visitCurrentTimeValueNode(const document::select::CurrentTimeValueNode &) override {}
    void visitStringValueNode(const document::select::StringValueNode &) override {}
    void visitNullValueNode(const document::select::NullValueNode &) override {}
    void visitInvalidValueNode(const document::select::InvalidValueNode &) override {}

    template <typename BinaryNode>
    void visitBothBranches(const BinaryNode & node) {
        node.getLeft().visit(*this);
        node.getRight().visit(*this);
    }
};

} // storage
