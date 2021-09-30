// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"

namespace vespalib::eval { struct Value; }

namespace document {

class TensorDataType;

/**
 * Field value representing a tensor.
 */
class TensorFieldValue : public FieldValue {
private:
    const TensorDataType &_dataType;
    std::unique_ptr<vespalib::eval::Value> _tensor;
    bool _altered;
public:
    TensorFieldValue();
    explicit TensorFieldValue(const TensorDataType &dataType);
    TensorFieldValue(const TensorFieldValue &rhs);
    TensorFieldValue(TensorFieldValue &&rhs);
    ~TensorFieldValue();

    TensorFieldValue &operator=(const TensorFieldValue &rhs);
    TensorFieldValue &operator=(std::unique_ptr<vespalib::eval::Value> rhs);

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
    const vespalib::eval::Value *getAsTensorPtr() const {
        return _tensor.get();
    }
    void assignDeserialized(std::unique_ptr<vespalib::eval::Value> rhs);
    virtual int compare(const FieldValue& other) const override;

    DECLARE_IDENTIFIABLE(TensorFieldValue);
};

} // document

