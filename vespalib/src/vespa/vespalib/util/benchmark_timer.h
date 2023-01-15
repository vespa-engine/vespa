// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>
#include <functional>
#include <algorithm>

namespace vespalib {

/**
 * Simple utility used to estimate how long something takes by doing
 * it repeatedly over a predefined time interval and remembering the
 * minimal time it took to do it.
 *
 * <pre>
 * Example:
 *
 * BenchmarkTimer timer(5.0);
 * while (timer.has_budget()) {
 *   timer.before();
 *   ... do stuff
 *   timer.after();
 * }
 * double min_time_s = timer.min_time()
 * </pre>
 *
 * As an even simpler alternative, the static benchmark functions can
 * be used to measure the time something takes as long as it can be
 * expressed as something that can be called repeatedly without input
 * or output. These functions use the BenchmarkTimer functionality
 * internally as described above, but also uses a baseline function to
 * compensate for overhead that should not be measured and also
 * internal loop iteration calibration to be able to measure things
 * that run really fast.
 *
 * <pre>
 * Example:
 *
 * double min_time_s = BenchmarkTimer::benchmark([](){... do stuff ...}, 1.0);
 * </pre>
 **/
class BenchmarkTimer
{
private:
    using clock = std::conditional<std::chrono::high_resolution_clock::is_steady,
                                   std::chrono::high_resolution_clock,
                                   std::chrono::steady_clock>::type;

    using seconds = std::chrono::duration<double, std::ratio<1,1>>;

    double _budget;
    double _min_time;
    clock::time_point _budget_start;
    clock::time_point _sample_start;

    static double elapsed(clock::time_point start) {
        clock::duration elapsed = (clock::now() - start);
        return std::chrono::duration_cast<seconds>(elapsed).count();
    }

    static void do_nothing() {}

    struct Loop {
        virtual void perform(size_t cnt) const = 0;
        virtual ~Loop() {}
    };

    struct Caller : Loop {
        std::function<void()> function;
        template <typename Callable> explicit Caller(Callable &&call_me) : function(call_me) {}
        void perform(size_t cnt) const override {
            for (size_t j = 0; j < cnt; ++j) {
                function();
            }
        }
    };

    static size_t calibrate(const Loop &loop) {
        for (size_t loop_cnt = 1; true; loop_cnt *= 2) {
            vespalib::BenchmarkTimer timer(0.0);
            for (size_t i = 0; i < 3; ++i) {
                timer.before();
                loop.perform(loop_cnt);
                timer.after();
            }
            if (timer.min_time() > 0.010) {
                return loop_cnt;
            }
        }
    }

    static double do_benchmark(const Loop &loop, size_t loop_cnt, double budget) {
        vespalib::BenchmarkTimer timer(budget);
        while (timer.has_budget()) {
            timer.before();
            loop.perform(loop_cnt);
            timer.after();
        }
        return (timer.min_time() / double(loop_cnt));
    }

public:
    explicit BenchmarkTimer(double budget)
        : _budget(budget), _min_time(-1.0),
          _budget_start(clock::now()), _sample_start(clock::now()) {}
    bool has_budget() {
        return (_min_time < 0.0 || elapsed(_budget_start) < _budget);
    }
    void before() {
        _sample_start = clock::now();
    }
    void after() {
        double new_time = elapsed(_sample_start);
        _min_time = (_min_time < 0.0 || new_time < _min_time) ? new_time : _min_time;
    }
    double min_time() const { return _min_time; }

    template <typename Callable1, typename Callable2>
    static double benchmark(Callable1 &&function, Callable2 &&baseline, size_t loop_cnt, double budget) {
        double overhead = do_benchmark(Caller(baseline), loop_cnt, budget * 0.2);
        double actual_time = do_benchmark(Caller(function), loop_cnt, budget * 0.8);
        return std::max(0.0, (actual_time - overhead));
    }

    template <typename Callable1, typename Callable2>
    static double benchmark(Callable1 &&function, Callable2 &&baseline, double budget) {
        return benchmark(function, baseline, calibrate(Caller(function)), budget);
    }

    template <typename Callable>
    static double benchmark(Callable &&function, double budget) {
        return benchmark(function, do_nothing, calibrate(Caller(function)), budget);
    }
};

} // namespace vespalib
