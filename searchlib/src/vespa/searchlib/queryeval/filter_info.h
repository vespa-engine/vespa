// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::queryeval {

// Information about filters that will impact hits produced
// by a query item.  Blueprints may use this to tune the
// implementation.
struct FilterInfo {
    double whitelist_ratio;
    bool is_inside_not;
    bool inverted;
    FilterInfo() : whitelist_ratio(1.0), is_inside_not(false), inverted(false) {}
};

}
