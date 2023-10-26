// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "utils.h"
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>

namespace search::features::util {

template <typename T>
T strToNum(vespalib::stringref str)
{
    vespalib::asciistream iss(str);
    T retval = 0;
    try {
        iss >> retval;
    } catch (const vespalib::IllegalArgumentException &) {
    }
    return retval;
}

}

