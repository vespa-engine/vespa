// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "annotationtype.h"
#include "numericdatatype.h"
#include "primitivedatatype.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/asciistream.h>

using std::vector;
using vespalib::string;

namespace document {
namespace {
AnnotationType makeType(int id, string name, const DataType &type) {
    AnnotationType annotation_type(id, name);
    annotation_type.setDataType(type);
    return annotation_type;
}

const PrimitiveDataType STRING_OBJ(DataType::T_STRING);
const NumericDataType INT_OBJ(DataType::T_INT);

const AnnotationType TERM_OBJ(makeType(1, "term", STRING_OBJ));
const AnnotationType TOKEN_TYPE_OBJ(makeType(2, "token_type", INT_OBJ));

}  // namespace

const AnnotationType *const AnnotationType::TERM(&TERM_OBJ);
const AnnotationType *const AnnotationType::TOKEN_TYPE(&TOKEN_TYPE_OBJ);

vector<const AnnotationType *> AnnotationType::getDefaultAnnotationTypes() {
    vector<const AnnotationType *> types;
    types.push_back(TERM);
    types.push_back(TOKEN_TYPE);
    return types;
}

vespalib::string
AnnotationType::toString() const {
    vespalib::asciistream os;
    os << *this;
    return os.str();
}

vespalib::asciistream &
operator << (vespalib::asciistream & os, const AnnotationType & a) {
    return os << "AnnotationType(" << a.getId() << ", " << a.getName() << ")";
}

}  // namespace document
