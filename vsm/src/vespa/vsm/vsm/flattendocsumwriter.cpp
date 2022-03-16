// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "flattendocsumwriter.h"
#include <vespa/document/fieldvalue/fieldvalues.h>

namespace vsm {

void
FlattenDocsumWriter::considerSeparator()
{
    if (_useSeparator) {
        _output.put(_separator.c_str(), _separator.size());
    }
}

void
FlattenDocsumWriter::onPrimitive(uint32_t, const Content & c)
{
    considerSeparator();
    const document::FieldValue & fv = c.getValue();
    if (fv.isLiteral()) {
        const document::LiteralFieldValueB & lfv = static_cast<const document::LiteralFieldValueB &>(fv);
        vespalib::stringref value = lfv.getValueRef();
        _output.put(value.data(), value.size());
    } else if (fv.isNumeric() ||
               fv.isA(document::FieldValue::Type::BOOL))
    {
        vespalib::string value = fv.getAsString();
        _output.put(value.data(), value.size());
    } else {
        vespalib::string value = fv.toString();
        _output.put(value.data(), value.size());
    }
    _useSeparator = true;
}

FlattenDocsumWriter::FlattenDocsumWriter(const vespalib::string & separator) :
    _output(32),
    _separator(separator),
    _useSeparator(false)
{ }

FlattenDocsumWriter::~FlattenDocsumWriter() = default;

}
