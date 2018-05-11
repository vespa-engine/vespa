// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "select_utils.h"
#include <vespa/document/select/valuenodes.h>

namespace proton {

vespalib::string
SelectUtils::extractFieldName(const document::select::FieldValueNode &expr, bool &isComplex)
{
    vespalib::string result = expr.getFieldName();
    isComplex = false;
    for (uint32_t i = 0; i < result.size(); ++i) {
        if (result[i] == '.' || result[i] == '{' || result[i] == '[') {
            // TODO: Check for struct, array, map or weigthed set
            result = expr.getFieldName().substr(0, i);
            isComplex = true;
            break;
        }
    }
    return result;
}

}
