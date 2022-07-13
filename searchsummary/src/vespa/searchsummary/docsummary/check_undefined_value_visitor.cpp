// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "check_undefined_value_visitor.h"
#include <vespa/searchcommon/common/undefinedvalues.h>
#include <vespa/document/fieldvalue/fieldvalues.h>

using search::attribute::isUndefined;


namespace search::docsummary {

void
CheckUndefinedValueVisitor::visit(const document::AnnotationReferenceFieldValue&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::ArrayFieldValue& value)
{
    if (value.isEmpty()) {
        _is_undefined = true;
    }
}

void
CheckUndefinedValueVisitor::visit(const document::BoolFieldValue&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::ByteFieldValue&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::Document&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::DoubleFieldValue& value)
{
    if (isUndefined(value.getValue())) {
        _is_undefined = true;
    }
}

void
CheckUndefinedValueVisitor::visit(const document::FloatFieldValue& value)
{
    if (isUndefined(value.getValue())) {
        _is_undefined = true;
    }
}

void
CheckUndefinedValueVisitor::visit(const document::IntFieldValue&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::LongFieldValue&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::MapFieldValue& value)
{
    if (value.isEmpty()) {
        _is_undefined = true;
    }
}

void
CheckUndefinedValueVisitor::visit(const document::PredicateFieldValue&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::RawFieldValue& value)
{
    if (value.getValueRef().empty()) {
        _is_undefined = true;
    }
}

void
CheckUndefinedValueVisitor::visit(const document::ShortFieldValue&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::StringFieldValue& value)
{
    if (isUndefined(value.getValue())) {
        _is_undefined = true;
    }
}

void
CheckUndefinedValueVisitor::visit(const document::StructFieldValue&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::WeightedSetFieldValue& value)
{
    if (value.isEmpty()) {
        _is_undefined = true;
    }
}

void
CheckUndefinedValueVisitor::visit(const document::TensorFieldValue&)
{
}

void
CheckUndefinedValueVisitor::visit(const document::ReferenceFieldValue&)
{
}

CheckUndefinedValueVisitor::CheckUndefinedValueVisitor()
    : _is_undefined(false)
{
}


CheckUndefinedValueVisitor::~CheckUndefinedValueVisitor() = default;

}
