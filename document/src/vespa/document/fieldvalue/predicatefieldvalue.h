// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldvalue.h"

namespace vespalib {
    class Slime;
}
namespace document {

class PredicateFieldValue final : public FieldValue {
    std::unique_ptr<vespalib::Slime> _slime;

    PredicateFieldValue & operator=(const PredicateFieldValue &rhs);
public:
    PredicateFieldValue();
    PredicateFieldValue(std::unique_ptr<vespalib::Slime> s);
    PredicateFieldValue(const PredicateFieldValue &rhs);
    PredicateFieldValue(PredicateFieldValue && rhs) noexcept = default;
    ~PredicateFieldValue() override;

    PredicateFieldValue & operator=(PredicateFieldValue && rhs) noexcept = default;

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    FieldValue *clone() const override;
    int compare(const FieldValue &rhs) const override;

    void printXml(XmlOutputStream &out) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;

    const DataType *getDataType() const override;

    const vespalib::Slime &getSlime() const { return *_slime; }

    FieldValue &assign(const FieldValue &rhs) override;
};

}
