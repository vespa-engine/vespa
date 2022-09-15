// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum_field_writer.h"

namespace search::docsummary {

const vespalib::string DocsumFieldWriter::_empty("");

const vespalib::string&
DocsumFieldWriter::getAttributeName() const
{
    return _empty;
}

bool
DocsumFieldWriter::isDefaultValue(uint32_t, const GetDocsumsState&) const
{
    return false;
}

bool
DocsumFieldWriter::setFieldWriterStateIndex(uint32_t)
{
    return false; // Don't need any field writer state by default
}

}
