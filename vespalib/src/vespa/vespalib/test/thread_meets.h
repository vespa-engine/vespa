// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/rendezvous.h>

namespace vespalib::test {

/**
 * Generally useful rendezvous implementations.
 **/
struct ThreadMeets {
    // can be used as a simple thread barrier
    struct Nop : vespalib::Rendezvous<bool,bool> {
        Nop(size_t N) : vespalib::Rendezvous<bool,bool>(N) {}
        void operator()() { rendezvous(false); }
        void mingle() override;
    };
    // swap values between 2 threads
    template <typename T>
    struct Swap : vespalib::Rendezvous<T,T> {
        using vespalib::Rendezvous<T,T>::in;
        using vespalib::Rendezvous<T,T>::out;
        using vespalib::Rendezvous<T,T>::rendezvous;
        Swap() : vespalib::Rendezvous<T,T>(2) {}
        T operator()(T input) { return rendezvous(input); }
        void mingle() override {
            out(1) = in(0);
            out(0) = in(1);
        }
    };
};

}
