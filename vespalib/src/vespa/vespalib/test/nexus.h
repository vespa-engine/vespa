// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "thread_meets.h"
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/require.h>
#include <optional>
#include <variant>

namespace vespalib::test {

class Nexus;
template <typename T>
concept nexus_thread_entry = requires(Nexus &ctx, T &&entry) {
    entry(ctx);
};

/**
 * Utility intended to make it easier to write multi-threaded code for
 * testing and benchmarking.
 **/
class Nexus
{
private:
    vespalib::test::ThreadMeets::Vote _vote;
    static size_t &my_thread_id();
public:
    constexpr static size_t invalid_thread_id = -1;
    Nexus(size_t num_threads) noexcept : _vote(num_threads) {}
    size_t num_threads() const noexcept { return _vote.size(); }
    size_t thread_id() const noexcept { return my_thread_id(); }
    bool vote(bool my_vote) { return _vote(my_vote); }
    void barrier() { REQUIRE_EQ(_vote(true), true); }
    struct select_thread_0 {};
    constexpr static auto merge_sum() { return [](auto a, auto b){ return a + b; }; }
    auto run(auto &&entry, auto &&merge) requires nexus_thread_entry<decltype(entry)> {
        ThreadPool pool;
        using result_t = std::decay_t<decltype(entry(std::declval<Nexus&>()))>;
        constexpr bool is_void = std::same_as<result_t, void>;
        using stored_t = std::conditional<is_void, std::monostate, result_t>::type;
        std::mutex lock;
        std::optional<stored_t> result;
        auto handle_result = [&](stored_t thread_result) noexcept {
            if constexpr (std::same_as<std::decay_t<decltype(merge)>,select_thread_0>) {
                if (thread_id() == 0) {
                    result = std::move(thread_result);
                }
            } else {
                std::lock_guard guard(lock);
                if (result.has_value()) {
                    result = merge(std::move(result).value(),
                                   std::move(thread_result));
                } else {
                    result = std::move(thread_result);
                }
            }
        };
        auto thread_main = [&](size_t thread_id) noexcept {
            size_t old_thread_id = my_thread_id();
            my_thread_id() = thread_id;
            if constexpr (is_void) {
                entry(*this);
            } else {
                handle_result(entry(*this));
            }
            my_thread_id() = old_thread_id;
        };
        for (size_t i = 1; i < num_threads(); ++i) {
            pool.start([i,&thread_main]() noexcept { thread_main(i); });
        }
        thread_main(0);
        pool.join();
        if constexpr (!is_void) {
            return std::move(result).value();
        }
    }
    auto run(auto &&entry) requires nexus_thread_entry<decltype(entry)> {
        return run(std::forward<decltype(entry)>(entry), select_thread_0{});
    }
    ~Nexus();
};

}
