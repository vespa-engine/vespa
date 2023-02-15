// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "thread.h"

namespace vespalib::thread {

std::thread start(Runnable &runnable, Runnable::init_fun_t init_fun_in) {
    return std::thread([&runnable, init_fun = std::move(init_fun_in)]()
                       {
                           init_fun(runnable);
                       });
}

}
