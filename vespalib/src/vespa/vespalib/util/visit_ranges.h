// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <functional>

namespace vespalib {

struct visit_ranges_either {};
struct visit_ranges_first : visit_ranges_either {};
struct visit_ranges_second : visit_ranges_either {};
struct visit_ranges_both {};

template <typename V, typename ItA, typename ItB, typename Cmp = std::less<> >
void visit_ranges(V &&visitor, ItA pos_a, ItA end_a, ItB pos_b, ItB end_b, Cmp cmp = Cmp()) {
    while ((pos_a != end_a) && (pos_b != end_b)) {
        if (cmp(*pos_a, *pos_b)) {
            visitor(visit_ranges_first(), *pos_a);
            ++pos_a;
        } else if (cmp(*pos_b, *pos_a)) {
            visitor(visit_ranges_second(), *pos_b);
            ++pos_b;
        } else {
            visitor(visit_ranges_both(), *pos_a, *pos_b);
            ++pos_a;
            ++pos_b;
        }
    }
    while (pos_a != end_a) {
        visitor(visit_ranges_first(), *pos_a);
        ++pos_a;
    }
    while (pos_b != end_b) {
        visitor(visit_ranges_second(), *pos_b);
        ++pos_b;
    }
}

}
