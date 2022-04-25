// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nice.h"

#include <unistd.h>
#include <algorithm>

namespace vespalib {

namespace {

void set_nice_value(double how_nice) {
    if (how_nice > 0.0) {
#ifndef __APPLE__
        int now = nice(0);
        int max = 19;
        int max_inc = (max - now);
        [[maybe_unused]] auto nice_result = nice(std::min(max_inc, int(how_nice * (max_inc + 1))));
#endif
    }
}

}

Runnable::init_fun_t be_nice(Runnable::init_fun_t init, double how_nice) {
    return [init,how_nice](Runnable &target) {
        set_nice_value(how_nice);
        return init(target);
    };
}

} // namespace
