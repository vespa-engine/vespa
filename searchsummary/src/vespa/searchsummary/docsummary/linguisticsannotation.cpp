// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "linguisticsannotation.h"
#include <vespa/document/datatype/primitivedatatype.h>

using document::AnnotationType;
using document::DataType;
using document::PrimitiveDataType;
using vespalib::string;

namespace search::linguistics {

namespace {
AnnotationType makeType(int id, string name, const DataType &type) {
    AnnotationType annotation_type(id, name);
    annotation_type.setDataType(type);
    return annotation_type;
}

const PrimitiveDataType STRING_OBJ(DataType::T_STRING);
AnnotationType TERM_OBJ(makeType(1, "term", STRING_OBJ));
}  // namespace

const string SPANTREE_NAME("linguistics");
const AnnotationType *const TERM(&TERM_OBJ);

}
