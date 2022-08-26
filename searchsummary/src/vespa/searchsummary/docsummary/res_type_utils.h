// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "res_type.h"

namespace search::docsummary {

/*
 * Utilitiy functions for checking if result type is ok.
 */
struct ResTypeUtils
{
    /**
     * @return the name of the given result field type.
     * @param resType enum value of a result field type.
     **/
    static const char *GetResTypeName(ResType type);
};

}
