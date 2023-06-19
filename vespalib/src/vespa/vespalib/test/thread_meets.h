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
        explicit Nop(size_t N) : vespalib::Rendezvous<bool,bool>(N) {}
        void operator()() { rendezvous(false); }
        void mingle() override;
    };
    // calculate the average value across threads
    struct Avg : Rendezvous<double, double> {
        explicit Avg(size_t n) : Rendezvous<double, double>(n) {}
        double operator()(double value) { return rendezvous(value); }
        void mingle() override;
    };
    // threads vote for true/false, majority wins (false on tie)
    struct Vote : Rendezvous<bool, bool> {
        explicit Vote(size_t n) : Rendezvous<bool, bool>(n) {}
        bool operator()(bool flag) { return rendezvous(flag); }
        void mingle() override;
    };
    // sum of values across all threads
    template <typename T>
    struct Sum : vespalib::Rendezvous<T,T> {
        using vespalib::Rendezvous<T,T>::in;
        using vespalib::Rendezvous<T,T>::out;
        using vespalib::Rendezvous<T,T>::size;
        using vespalib::Rendezvous<T,T>::rendezvous;
        explicit Sum(size_t N) : vespalib::Rendezvous<T,T>(N) {}
        T operator()(T value) { return rendezvous(value); }
        void mingle() override {
            T acc{};
            for (size_t i = 0; i < size(); ++i) {
                acc += in(i);
            }
            for (size_t i = 0; i < size(); ++i) {
                out(i) = acc;
            }
        }
    };
    // range of values across all threads
    template <typename T>
    struct Range : vespalib::Rendezvous<T,T> {
        using vespalib::Rendezvous<T,T>::in;
        using vespalib::Rendezvous<T,T>::out;
        using vespalib::Rendezvous<T,T>::size;
        using vespalib::Rendezvous<T,T>::rendezvous;
        explicit Range(size_t N) : vespalib::Rendezvous<T,T>(N) {}
        T operator()(T value) { return rendezvous(value); }
        void mingle() override {
            T min = in(0);
            T max = in(0);
            for (size_t i = 1; i < size(); ++i) {
                if (in(i) < min) {
                    min = in(i);
                }
                if (in(i) > max) {
                    max = in(i);
                }
            }
            T result = (max - min);
            for (size_t i = 0; i < size(); ++i) {
                out(i) = result;
            }
        }
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
            out(1) = std::move(in(0));
            out(0) = std::move(in(1));
        }
    };
};

}
