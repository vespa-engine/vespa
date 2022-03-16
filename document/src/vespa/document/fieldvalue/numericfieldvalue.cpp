// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "numericfieldvalue.h"
#include "numericfieldvalue.hpp"
#include <vespa/vespalib/util/xmlstream.h>

namespace document {

void
NumericFieldValueBase::printXml(XmlOutputStream& out) const
{
    out << vespalib::xml::XmlContent(getAsString());
}

template class NumericFieldValue<float>;
template class NumericFieldValue<double>;
template class NumericFieldValue<int8_t>;
template class NumericFieldValue<int16_t>;
template class NumericFieldValue<int32_t>;
template class NumericFieldValue<int64_t>;

} // document
