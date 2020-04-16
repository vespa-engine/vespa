// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"

namespace vespalib { namespace tensor { class Tensor; } }

namespace document {

class TensorDataType;

/**
 * Field value representing a tensor.
 */
class TensorFieldValue : public FieldValue {
private:
    const TensorDataType &_dataType;
    std::unique_ptr<vespalib::tensor::Tensor> _tensor;
    bool _altered;
public:
    TensorFieldValue();
    explicit TensorFieldValue(const TensorDataType &dataType);
    TensorFieldValue(const TensorFieldValue &rhs);
    TensorFieldValue(TensorFieldValue &&rhs);
    ~TensorFieldValue();

    TensorFieldValue &operator=(const TensorFieldValue &rhs);
    TensorFieldValue &operator=(std::unique_ptr<vespalib::tensor::Tensor> rhs);

    void make_empty_if_not_existing();

    virtual void accept(FieldValueVisitor &visitor) override;
    virtual void accept(ConstFieldValueVisitor &visitor) const override;
    virtual const DataType *getDataType() const override;
    virtual bool hasChanged() const override;
    virtual TensorFieldValue* clone() const override;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const override;
    virtual void printXml(XmlOutputStream& out) const override;
    virtual FieldValue &assign(const FieldValue &value) override;
    const std::unique_ptr<vespalib::tensor::Tensor> &getAsTensorPtr() const {
        return _tensor;
    }
    void assignDeserialized(std::unique_ptr<vespalib::tensor::Tensor> rhs);
    virtual int compare(const FieldValue& other) const override;

    DECLARE_IDENTIFIABLE(TensorFieldValue);
};

} // document

