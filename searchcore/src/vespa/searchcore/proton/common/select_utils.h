// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace document::select { class FieldValueNode; }

namespace proton {

/**
 * Utility functions used as part of document selection integration in searchcore.
 */
struct SelectUtils {

    /**
     * Extracts the field name of the FieldValueNode and signals whether it is complex or not.
     */
    static vespalib::string extractFieldName(const document::select::FieldValueNode &expr, bool &isComplex);

};

}
