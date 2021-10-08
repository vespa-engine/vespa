// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicatefieldvalue.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/predicate/predicate.h>
#include <vespa/document/predicate/predicate_printer.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/util/xmlstream.h>

using vespalib::Slime;
using vespalib::slime::SlimeInserter;
using namespace vespalib::xml;

namespace document {

IMPLEMENT_IDENTIFIABLE(PredicateFieldValue, FieldValue);

PredicateFieldValue::PredicateFieldValue()
    : _slime(std::make_unique<Slime>()), _altered(false) {
}

PredicateFieldValue::PredicateFieldValue(vespalib::Slime::UP s)
    : FieldValue(),
      _slime(std::move(s)),
      _altered(false)
{ }

PredicateFieldValue::PredicateFieldValue(const PredicateFieldValue &rhs)
    : FieldValue(rhs),
      _slime(new Slime),
      _altered(rhs._altered)
{
    inject(rhs._slime->get(), SlimeInserter(*_slime));
}

PredicateFieldValue::~PredicateFieldValue() = default;

FieldValue &
PredicateFieldValue::assign(const FieldValue &rhs) {
    if (rhs.inherits(PredicateFieldValue::classId)) {
        operator=(static_cast<const PredicateFieldValue &>(rhs));
    } else {
        _slime.reset();
        _altered = true;
    }
    return *this;
}

PredicateFieldValue &
PredicateFieldValue::operator=(const PredicateFieldValue &rhs)
{
    _slime = std::make_unique<Slime>();
    inject(rhs._slime->get(), SlimeInserter(*_slime));
    _altered = true;
    return *this;
}

int
PredicateFieldValue::compare(const FieldValue&rhs) const {
    int diff = FieldValue::compare(rhs);
    if (diff != 0) return diff;
    const PredicateFieldValue &o = static_cast<const PredicateFieldValue &>(rhs);
    return Predicate::compare(*_slime, *o._slime);
}

void
PredicateFieldValue::printXml(XmlOutputStream& out) const {
    out << XmlContent(PredicatePrinter::print(*_slime));
}

void
PredicateFieldValue::print(std::ostream& out, bool, const std::string&) const {
    out << PredicatePrinter::print(*_slime) << "\n";
}

const DataType *
PredicateFieldValue::getDataType() const {
    return DataType::PREDICATE;
}

bool
PredicateFieldValue::hasChanged() const {
    return _altered;
}

FieldValue *
PredicateFieldValue::clone() const {
    return new PredicateFieldValue(*this);
}

}  // namespace document
