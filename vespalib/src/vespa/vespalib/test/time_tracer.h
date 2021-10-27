// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/stash.h>
#include <chrono>
#include <mutex>
#include <atomic>
#include <thread>
#include <map>

namespace vespalib::test {

/**
 * Keep track of when and for how long different things happen across
 * different threads. Note that this is intended for testing purposes
 * only, since collected data is never released.
 *
 * Typically, timing information is collected while a unit test is
 * running. Sampling can be performed in the test code itself to
 * identify which parts take more time. For more advanced analysis,
 * critical parts of the code being tested can also be modified to
 * collect time samples (this is similar to the ancient fprintf
 * debugging technique).
 *
 * In order to collect timing information, the client code uses the
 * TT_Tag and TT_Sample classes. The TT_Tag class represents a thing
 * that can happen. It should always be constructed up front. The
 * TT_Sample class represents the fact that the thing represented by a
 * TT_Tag is happening for as long as the TT_Sample object is
 * alive. In other words; if the TT_Tag is an event, the TT_Sample
 * will bind an instance of that event to the current scope.
 *
 * <pre>
 * How to integrate into code:
 *
 * #include <vespa/vespalib/test/time_tracer.h>
 *
 * TT_Tag my_tag("my task");
 *
 * void do_stuff() {
 *     TT_Sample my_sample(my_tag);
 *     ... perform 'my task'
 * }
 * </pre>
 *
 * Data collection is designed to be high-capacity/low-overhead. This
 * means that collected time samples cannot be inspected directly in a
 * global structure but will need to be extracted before they can be
 * analyzed.
 **/
class TimeTracer
{
public:
    using time_point = std::chrono::steady_clock::time_point;
    static time_point now() { return std::chrono::steady_clock::now(); }

    class Tag {
    private:
        uint32_t _id;
    public:
        Tag(const vespalib::string &name_in);
        ~Tag();
        uint32_t id() const { return _id; }
    };

    class Sample {
    private:
        uint32_t _tag_id;
        time_point _start;
    public:
        Sample(const Tag &tag) noexcept : _tag_id(tag.id()), _start(now()) {}
        ~Sample() noexcept { thread_state().add_log_entry(_tag_id, _start, now()); }
    };

    struct Record {
        uint32_t thread_id;
        uint32_t tag_id;
        time_point start;
        time_point stop;
        Record(uint32_t thread_id_in, uint32_t tag_id_in,
               time_point start_in, time_point stop_in)
            : thread_id(thread_id_in), tag_id(tag_id_in),
              start(start_in), stop(stop_in) {}
        double ms_duration() const;
        vespalib::string tag_name() const;
    };

    class Extractor {
    private:
        bool       _by_thread;
        uint32_t   _thread_id;
        bool       _by_tag;
        uint32_t   _tag_id;
        bool       _by_time;
        time_point _a;
        time_point _b;
    public:
        bool keep(const Record &entry) const;
        Extractor &&by_thread(uint32_t thread_id) &&;
        Extractor &&by_tag(uint32_t tag_id) &&;
        Extractor &&by_time(time_point a, time_point b) &&;
        std::vector<Record> get() const;
        ~Extractor();
    };

private:
    struct LogEntry {
        uint32_t tag_id;
        time_point start;
        time_point stop;
        const LogEntry *next;
        LogEntry(uint32_t tag_id_in, time_point start_in, time_point stop_in, const LogEntry *next_in)
            : tag_id(tag_id_in), start(start_in), stop(stop_in), next(next_in) {}
    };

    struct Guard {
        std::atomic_flag &lock;
        explicit Guard(std::atomic_flag &lock_in) noexcept : lock(lock_in) {
            while (__builtin_expect(lock.test_and_set(std::memory_order_acquire), false)) {
                std::this_thread::yield();
            }
        }
        ~Guard() noexcept { lock.clear(std::memory_order_release); }
    };

    class ThreadState {
    private:
        uint32_t _thread_id;
        mutable std::atomic_flag _lock = ATOMIC_FLAG_INIT;
        vespalib::Stash _stash;
        const LogEntry * _list;
    public:
        using UP = std::unique_ptr<ThreadState>;
        ThreadState(uint32_t thread_id)
            : _thread_id(thread_id), _stash(64 * 1024), _list(nullptr) {}
        uint32_t thread_id() const { return _thread_id; }
        const LogEntry *get_log_entries() const {
            Guard guard(_lock);
            return _list;
        }
        void add_log_entry(uint32_t tag_id, time_point start, time_point stop) {
            Guard guard(_lock);
            _list = &_stash.create<LogEntry>(tag_id, start, stop, _list);
        }
    };
    static TimeTracer &master();
    static thread_local ThreadState *_thread_state;

    static void init_thread_state() noexcept;
    static ThreadState &thread_state() noexcept {
        if (__builtin_expect((_thread_state == nullptr), false)) {
            init_thread_state();
        }
        return *_thread_state;
    }

    std::mutex _lock;
    std::vector<ThreadState::UP> _state_list;
    std::map<vespalib::string, uint32_t> _tags;
    std::vector<vespalib::string> _tag_names;

    TimeTracer();
    ~TimeTracer();
    uint32_t get_tag_id(const vespalib::string &tag_name);
    vespalib::string get_tag_name(uint32_t tag_id);
    ThreadState *create_thread_state();
    std::vector<Record> extract_impl(const Extractor &extractor);

public:
    static Extractor extract() { return Extractor(); }
};

} // namespace vespalib::test

using TT_Tag = vespalib::test::TimeTracer::Tag;
using TT_Sample = vespalib::test::TimeTracer::Sample;
