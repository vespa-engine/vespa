// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document {

class DocumentUpdate;
class FieldUpdate;
class RemoveValueUpdate;
class AddValueUpdate;
class ArithmeticValueUpdate;
class AssignValueUpdate;
class ClearValueUpdate;
class MapValueUpdate;
class AddFieldPathUpdate;
class AssignFieldPathUpdate;
class RemoveFieldPathUpdate;
class TensorAddUpdate;
class TensorModifyUpdate;
class TensorRemoveUpdate;

struct UpdateVisitor {
    virtual ~UpdateVisitor() {}

    virtual void visit(const DocumentUpdate &value) = 0;
    virtual void visit(const FieldUpdate &value) = 0;
    virtual void visit(const RemoveValueUpdate &value) = 0;
    virtual void visit(const AddValueUpdate &value) = 0;
    virtual void visit(const ArithmeticValueUpdate &value) = 0;
    virtual void visit(const AssignValueUpdate &value) = 0;
    virtual void visit(const ClearValueUpdate &value) = 0;
    virtual void visit(const MapValueUpdate &value) = 0;
    virtual void visit(const AddFieldPathUpdate &value) = 0;
    virtual void visit(const AssignFieldPathUpdate &value) = 0;
    virtual void visit(const RemoveFieldPathUpdate &value) = 0;
    virtual void visit(const TensorModifyUpdate &value) = 0;
    virtual void visit(const TensorAddUpdate &value) = 0;
    virtual void visit(const TensorRemoveUpdate &value) = 0;
};

#define ACCEPT_UPDATE_VISITOR void accept(UpdateVisitor & visitor) const override { visitor.visit(*this); }

}  // namespace document

