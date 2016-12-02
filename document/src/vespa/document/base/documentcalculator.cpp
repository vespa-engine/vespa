// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentcalculator.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/select/compare.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/select/valuenode.h>
#include <vespa/vespalib/util/exceptions.h>

namespace document {

DocumentCalculator::DocumentCalculator(
        const DocumentTypeRepo& repo,
        const vespalib::string & expression)
{
    BucketIdFactory factory;
    select::Parser parser(repo, factory);
    _selectionNode = parser.parse(expression + " == 0");
}

double
DocumentCalculator::evaluate(const Document& doc, VariableMap& variables)
{
    select::Compare& compare(static_cast<select::Compare&>(*_selectionNode));
    const select::ValueNode& left = compare.getLeft();

    select::Context context(doc);
    context._variables = variables;
    std::unique_ptr<select::Value> value = left.getValue(context);

    select::NumberValue* num = dynamic_cast<select::NumberValue*>(value.get());

    if (!num) {
        throw vespalib::IllegalArgumentException("Expression could not be evaluated - some components of the expression may be missing");
    }

    return num->getCommonValue();
}

}
