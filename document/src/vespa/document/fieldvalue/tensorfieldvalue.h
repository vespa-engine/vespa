// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"

namespace vespalib::eval { struct Value; }

namespace document {

class TensorDataType;

/**
 * Field value representing a tensor.
 */
class TensorFieldValue final : public FieldValue {
private:
    const TensorDataType &_dataType;
    std::unique_ptr<vespalib::eval::Value> _tensor;
public:
    TensorFieldValue();
    explicit TensorFieldValue(const TensorDataType &dataType);
    TensorFieldValue(const TensorFieldValue &rhs);
    TensorFieldValue(TensorFieldValue &&rhs);
    ~TensorFieldValue() override;

    TensorFieldValue &operator=(const TensorFieldValue &rhs);
    TensorFieldValue &operator=(std::unique_ptr<vespalib::eval::Value> rhs);

    void make_empty_if_not_existing();

    void accept(FieldValueVisitor &visitor) override;
    void accept(ConstFieldValueVisitor &visitor) const override;
    const DataType *getDataType() const override;
    TensorFieldValue* clone() const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void printXml(XmlOutputStream& out) const override;
    FieldValue &assign(const FieldValue &value) override;
    const vespalib::eval::Value *getAsTensorPtr() const {
        return _tensor.get();
    }
    void assignDeserialized(std::unique_ptr<vespalib::eval::Value> rhs);
    int compare(const FieldValue& other) const override;
};

} // document

