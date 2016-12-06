// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include "fieldvalue.h"

namespace vespalib {
    class Slime;
}
namespace document {

class PredicateFieldValue : public FieldValue {
    std::unique_ptr<vespalib::Slime> _slime;
    bool _altered;

public:
    PredicateFieldValue();
    PredicateFieldValue(std::unique_ptr<vespalib::Slime> s);
    PredicateFieldValue(const PredicateFieldValue &rhs);
    ~PredicateFieldValue();

    PredicateFieldValue &operator=(const PredicateFieldValue &rhs);

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    virtual FieldValue *clone() const override { return new PredicateFieldValue(*this); }
    virtual int compare(const FieldValue &rhs) const override;

    virtual void printXml(XmlOutputStream &out) const override;
    virtual void print(std::ostream &out, bool verbose, const std::string &indent) const override;

    virtual const DataType *getDataType() const override { return DataType::PREDICATE; }
    virtual bool hasChanged() const override { return _altered; }

    const vespalib::Slime &getSlime() const { return *_slime; }

    virtual FieldValue &assign(const FieldValue &rhs) override;

DECLARE_IDENTIFIABLE(PredicateFieldValue);
};

}  // namespace document

