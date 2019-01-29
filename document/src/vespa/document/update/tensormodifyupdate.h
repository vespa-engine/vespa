// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "valueupdate.h"

namespace vespalib { namespace tensor { class Tensor; } }

namespace document {

/*
 * An update for a subset of the cells in a tensor.
 * The operand is represented as a mapped (aka sparse) tensor.
 */
class TensorModifyUpdate : public ValueUpdate {
public:
    /** Declare all types of tensor modify updates. */
    enum class Operator { // Operation to be applied to matching tensor cells
        REPLACE = 0,
        ADD     = 1,
        MUL     = 2,
        MAX_NUM_OPERATORS = 3
    };
private:
    Operator _operator;
    std::unique_ptr<vespalib::tensor::Tensor> _operand;

    TensorModifyUpdate();
    TensorModifyUpdate(const TensorModifyUpdate &rhs);
    ACCEPT_UPDATE_VISITOR;
public:
    TensorModifyUpdate(Operator op, std::unique_ptr<vespalib::tensor::Tensor> &&operand);
    ~TensorModifyUpdate() override;
    TensorModifyUpdate &operator=(const TensorModifyUpdate &rhs);
    TensorModifyUpdate &operator=(TensorModifyUpdate &&rhs);
    bool operator==(const ValueUpdate &other) const override;
    Operator getOperator() const { return _operator; }
    const vespalib::tensor::Tensor &getOperand() const { return *_operand; }
    void checkCompatibility(const Field &field) const override;
    bool applyTo(FieldValue &value) const override;
    void printXml(XmlOutputStream &xos) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;
    void deserialize(const DocumentTypeRepo &, const DataType &, nbostream &stream) override;
    TensorModifyUpdate* clone() const override;

    DECLARE_IDENTIFIABLE(TensorModifyUpdate);
};

}
