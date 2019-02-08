// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "valueupdate.h"

namespace vespalib::tensor { struct Tensor; }

namespace document {

class TensorFieldValue;

/*
 *  An update used to add cells to a sparse tensor (has only mapped dimensions).
 *
 *  The cells to add are contained in a sparse tensor as well.
 */
class TensorAddUpdate : public ValueUpdate {
    std::unique_ptr<TensorFieldValue> _tensor;

    TensorAddUpdate();
    TensorAddUpdate(const TensorAddUpdate &rhs);
    ACCEPT_UPDATE_VISITOR;
public:
    TensorAddUpdate(std::unique_ptr<TensorFieldValue> &&tensor);
    ~TensorAddUpdate() override;
    TensorAddUpdate &operator=(const TensorAddUpdate &rhs);
    TensorAddUpdate &operator=(TensorAddUpdate &&rhs);
    bool operator==(const ValueUpdate &other) const override;
    const TensorFieldValue &getTensor() const { return *_tensor; }
    void checkCompatibility(const Field &field) const override;
    std::unique_ptr<vespalib::tensor::Tensor> applyTo(const vespalib::tensor::Tensor &tensor) const;
    bool applyTo(FieldValue &value) const override;
    void printXml(XmlOutputStream &xos) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;
    void deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream &stream) override;
    TensorAddUpdate* clone() const override;

    DECLARE_IDENTIFIABLE(TensorAddUpdate);
};

}
