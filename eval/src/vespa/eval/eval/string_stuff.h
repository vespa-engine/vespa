// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tensor_spec.h"
#include <vespa/vespalib/stllike/string.h>

namespace vespalib::eval {

/**
 * Helper class used to insert commas on the appropriate places in
 * comma-separated textual lists.
 **/
struct CommaTracker {
    bool first;
    CommaTracker() : first(true) {}
    void maybe_comma(vespalib::string &dst) {
        if (first) {
            first = false;
        } else {
            dst.push_back(',');
        }
    }
};

/**
 * Convert a tensor spec address into a string on the form:
 * '{dim1:label,dim2:index, ...}'
 **/
vespalib::string as_string(const TensorSpec::Address &address);

}
