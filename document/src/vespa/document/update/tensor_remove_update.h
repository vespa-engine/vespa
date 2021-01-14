// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_update.h"
#include "valueupdate.h"

namespace vespalib::eval { struct Value; }

namespace document {

class TensorDataType;
class TensorFieldValue;

/**
 * An update used to remove cells from a sparse or mixed tensor.
 *
 * The cells to remove are contained in a sparse tensor (with all mapped dimensions) where cell values are set to 1.0.
 * When used on a mixed tensor the entire dense sub-space (pointed to by a cell in the sparse tensor) is removed.
 */
class TensorRemoveUpdate : public ValueUpdate, public TensorUpdate {
private:
    std::unique_ptr<const TensorDataType> _tensorType;
    std::unique_ptr<TensorFieldValue> _tensor;

    TensorRemoveUpdate();
    TensorRemoveUpdate(const TensorRemoveUpdate &rhs);
    ACCEPT_UPDATE_VISITOR;

public:
    TensorRemoveUpdate(std::unique_ptr<TensorFieldValue> tensor);
    ~TensorRemoveUpdate() override;
    TensorRemoveUpdate &operator=(const TensorRemoveUpdate &rhs);
    TensorRemoveUpdate &operator=(TensorRemoveUpdate &&rhs);
    const TensorFieldValue &getTensor() const { return *_tensor; }
    std::unique_ptr<vespalib::eval::Value> applyTo(const vespalib::eval::Value &tensor) const;
    std::unique_ptr<Value> apply_to(const Value &tensor,
                                    const ValueBuilderFactory &factory) const override;
    bool operator==(const ValueUpdate &other) const override;
    void checkCompatibility(const Field &field) const override;
    bool applyTo(FieldValue &value) const override;
    void printXml(XmlOutputStream &xos) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;
    void deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream &stream) override;
    TensorRemoveUpdate* clone() const override;

    DECLARE_IDENTIFIABLE(TensorRemoveUpdate);
};

}
