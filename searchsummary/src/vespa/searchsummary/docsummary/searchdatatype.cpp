// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".search.docsummary.searchdatatype");

#include "searchdatatype.h"
#include <vespa/document/base/field.h>
#include <vespa/document/datatype/primitivedatatype.h>
#include <vespa/document/datatype/structdatatype.h>

using document::DataType;
using document::Field;
using document::PrimitiveDataType;
using document::StructDataType;

namespace search {
namespace docsummary {

namespace {

PrimitiveDataType STRING_OBJ(DataType::T_STRING);
StructDataType URI_OBJ("url");

const StructDataType *setUpUriType() {
    URI_OBJ.addField(Field("all", STRING_OBJ, true));
    URI_OBJ.addField(Field("scheme", STRING_OBJ, true));
    URI_OBJ.addField(Field("host", STRING_OBJ, true));
    URI_OBJ.addField(Field("port", STRING_OBJ, true));
    URI_OBJ.addField(Field("path", STRING_OBJ, true));
    URI_OBJ.addField(Field("query", STRING_OBJ, true));
    URI_OBJ.addField(Field("fragment", STRING_OBJ, true));
    return &URI_OBJ;
}
}  // namespace

const DataType *SearchDataType::URI(setUpUriType());

}  // namespace search::docsummary
}  // namespace search
