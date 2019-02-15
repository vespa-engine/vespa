// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "valueupdate.h"

namespace vespalib::tensor { class Tensor; }

namespace document {

class TensorFieldValue;

/**
 * An update used to remove cells from a sparse tensor (has only mapped dimensions).
 *
 * The cells to remove are contained in a sparse tensor as well.
 */
class TensorRemoveUpdate : public ValueUpdate {
private:
    std::unique_ptr<TensorFieldValue> _tensor;

    TensorRemoveUpdate();
    TensorRemoveUpdate(const TensorRemoveUpdate &rhs);
    ACCEPT_UPDATE_VISITOR;

public:
    TensorRemoveUpdate(std::unique_ptr<TensorFieldValue> &&tensor);
    ~TensorRemoveUpdate() override;
    TensorRemoveUpdate &operator=(const TensorRemoveUpdate &rhs);
    TensorRemoveUpdate &operator=(TensorRemoveUpdate &&rhs);
    const TensorFieldValue &getTensor() const { return *_tensor; }
    std::unique_ptr<vespalib::tensor::Tensor> applyTo(const vespalib::tensor::Tensor &tensor) const;

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
