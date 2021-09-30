// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_update.h"
#include "valueupdate.h"

namespace vespalib::eval { struct Value; }

namespace document {

class TensorDataType;
class TensorFieldValue;

/*
 * An update for a subset of the cells in a tensor.
 *
 * The operand is represented as a tensor field value containing a
 * mapped (aka sparse) tensor.
 */
class TensorModifyUpdate : public ValueUpdate, public TensorUpdate {
public:
    /** Declare all types of tensor modify updates. */
    enum class Operation { // Operation to be applied to matching tensor cells
        REPLACE  = 0,
        ADD      = 1,
        MULTIPLY = 2,
        MAX_NUM_OPERATIONS = 3
    };
private:
    Operation _operation;
    std::unique_ptr<const TensorDataType> _tensorType;
    std::unique_ptr<TensorFieldValue> _tensor;

    TensorModifyUpdate();
    TensorModifyUpdate(const TensorModifyUpdate &rhs);
    ACCEPT_UPDATE_VISITOR;
public:
    TensorModifyUpdate(Operation operation, std::unique_ptr<TensorFieldValue> tensor);
    ~TensorModifyUpdate() override;
    TensorModifyUpdate &operator=(const TensorModifyUpdate &rhs);
    TensorModifyUpdate &operator=(TensorModifyUpdate &&rhs);
    bool operator==(const ValueUpdate &other) const override;
    Operation getOperation() const { return _operation; }
    const TensorFieldValue &getTensor() const { return *_tensor; }
    void checkCompatibility(const Field &field) const override;
    std::unique_ptr<vespalib::eval::Value> applyTo(const vespalib::eval::Value &tensor) const;
    std::unique_ptr<Value> apply_to(const Value &tensor,
                                    const ValueBuilderFactory &factory) const override;
    bool applyTo(FieldValue &value) const override;
    void printXml(XmlOutputStream &xos) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;
    void deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream &stream) override;
    TensorModifyUpdate* clone() const override;

    DECLARE_IDENTIFIABLE(TensorModifyUpdate);
};

}
