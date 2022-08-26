// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "primitivedatatype.h"
#include <vespa/eval/eval/value_type.h>

namespace document {

/*
 * This class describes a tensor type.
 */
class TensorDataType final : public PrimitiveDataType {
    vespalib::eval::ValueType _tensorType;
public:
    TensorDataType(vespalib::eval::ValueType tensorType);
    TensorDataType(const TensorDataType &);  //TODO try to avoid
    TensorDataType & operator=(const TensorDataType &) = delete;
    ~TensorDataType();

    bool isTensor() const noexcept override { return true; }
    virtual const TensorDataType* cast_tensor() const noexcept override { return this; }
    bool equals(const DataType& other) const noexcept override;
    std::unique_ptr<FieldValue> createFieldValue() const override;
    void print(std::ostream&, bool verbose, const std::string& indent) const override;
    static std::unique_ptr<const TensorDataType> fromSpec(const vespalib::string &spec);
    const vespalib::eval::ValueType &getTensorType() const { return _tensorType; }
    bool isAssignableType(const vespalib::eval::ValueType &tensorType) const;
    static bool isAssignableType(const vespalib::eval::ValueType &fieldTensorType, const vespalib::eval::ValueType &tensorType);
};

}
