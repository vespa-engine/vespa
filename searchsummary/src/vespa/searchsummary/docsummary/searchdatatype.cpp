// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchdatatype.h"
#include <vespa/document/datatype/primitivedatatype.h>
#include <vespa/document/datatype/structdatatype.h>

using document::DataType;
using document::Field;
using document::PrimitiveDataType;
using document::StructDataType;

namespace search::docsummary {

namespace {

PrimitiveDataType STRING_OBJ(DataType::T_STRING);
StructDataType URI_OBJ("url");

const StructDataType *setUpUriType() {
    URI_OBJ.addField(Field("all", STRING_OBJ));
    URI_OBJ.addField(Field("scheme", STRING_OBJ));
    URI_OBJ.addField(Field("host", STRING_OBJ));
    URI_OBJ.addField(Field("port", STRING_OBJ));
    URI_OBJ.addField(Field("path", STRING_OBJ));
    URI_OBJ.addField(Field("query", STRING_OBJ));
    URI_OBJ.addField(Field("fragment", STRING_OBJ));
    return &URI_OBJ;
}
}  // namespace

const DataType *SearchDataType::URI(setUpUriType());

}
