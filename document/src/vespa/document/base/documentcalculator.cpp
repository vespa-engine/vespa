// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentcalculator.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/select/compare.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/select/valuenode.h>
#include <vespa/document/select/variablemap.h>
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

DocumentCalculator::~DocumentCalculator() { }

double
DocumentCalculator::evaluate(const Document& doc, std::unique_ptr<select::VariableMap> variables)
{
    select::Compare& compare(static_cast<select::Compare&>(*_selectionNode));
    const select::ValueNode& left = compare.getLeft();

    select::Context context(doc);
    context.setVariableMap(std::move(variables));
    std::unique_ptr<select::Value> value = left.getValue(context);

    select::NumberValue* num = dynamic_cast<select::NumberValue*>(value.get());

    if (!num) {
        throw vespalib::IllegalArgumentException("Expression could not be evaluated - some components of the expression may be missing");
    }

    return num->getCommonValue();
}

}
