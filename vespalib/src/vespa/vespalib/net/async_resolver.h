// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "socket_address.h"
#include "socket_spec.h"
#include <vespa/vespalib/util/threadexecutor.h>
#include <vespa/vespalib/util/arrayqueue.hpp>
#include <chrono>
#include <memory>
#include <mutex>
#include <map>

namespace vespalib {

/**
 * Component used to perform asynchronous resolving of connect
 * specs. Internal worker threads are used to perform synchronous
 * resolving with caching. Results are delivered to a result handler
 * that is tracked using a weak pointer while the operation is
 * pending. This enables us to skip resolving specs that are no longer
 * needed by the client. Use the get_shared function to obtain a
 * shared default-constructed instance. It will be created on the
 * first call and cleaned up on program exit.
 **/
class AsyncResolver
{
public:
    using SP = std::shared_ptr<AsyncResolver>;
    using time_point = std::chrono::steady_clock::time_point;
    using seconds = std::chrono::duration<double>;

    struct ResultHandler {
        virtual void handle_result(SocketAddress addr) = 0;
        virtual ~ResultHandler() {}
        using SP = std::shared_ptr<ResultHandler>;
        using WP = std::weak_ptr<ResultHandler>;
    };

    struct Clock {
        virtual time_point now() = 0;
        virtual ~Clock() {}
        using SP = std::shared_ptr<Clock>;
    };

    struct HostResolver {
        virtual vespalib::string ip_address(const vespalib::string &host_name) = 0;
        virtual ~HostResolver() {}
        using SP = std::shared_ptr<HostResolver>;
    };

    struct SteadyClock : public Clock {
        time_point now() override;
    };

    struct SimpleHostResolver : public HostResolver {
        vespalib::string ip_address(const vespalib::string &host_name) override;
    };

    struct Params {
        Clock::SP        clock;
        HostResolver::SP resolver;
        size_t           max_cache_size;
        seconds          max_result_age;
        seconds          max_resolve_time;
        size_t           num_threads;
        Params();
        ~Params() {}
    };

private:
    class LoggingHostResolver : public HostResolver {
    private:
        Clock::SP _clock;
        HostResolver::SP _resolver;
        seconds _max_resolve_time;
    public:
        LoggingHostResolver(Clock::SP clock, HostResolver::SP resolver, seconds max_resolve_time) noexcept
            : _clock(std::move(clock)), _resolver(std::move(resolver)), _max_resolve_time(max_resolve_time) {}
        vespalib::string ip_address(const vespalib::string &host_name) override;
    };

    class CachingHostResolver : public HostResolver {
    private:
        struct Entry {
            vespalib::string  ip_address;
            time_point        end_time;
            Entry(const vespalib::string &ip, time_point end)
                : ip_address(ip), end_time(end) {}
        };
        using Map = std::map<vespalib::string,Entry>;
        using Itr = Map::iterator;
        Clock::SP        _clock;
        HostResolver::SP _resolver;
        size_t           _max_cache_size;
        seconds          _max_result_age;
        std::mutex       _lock;
        Map              _map;
        ArrayQueue<Itr>  _queue;

        bool should_evict_oldest_entry(const std::lock_guard<std::mutex> &guard, time_point now);
        bool lookup(const vespalib::string &host_name, vespalib::string &ip_address);
        void resolve(const vespalib::string &host_name, vespalib::string &ip_address);
        void store(const vespalib::string &host_name, const vespalib::string &ip_address);

    public:
        CachingHostResolver(Clock::SP clock, HostResolver::SP resolver, size_t max_cache_size, seconds max_result_age) noexcept;
        vespalib::string ip_address(const vespalib::string &host_name) override;
    };

    struct ResolveTask : public Executor::Task {
        vespalib::string spec;
        HostResolver &resolver;
        ResultHandler::WP weak_handler;
        ResolveTask(const vespalib::string &spec_in, HostResolver &resolver_in, ResultHandler::WP weak_handler_in)
            : spec(spec_in), resolver(resolver_in), weak_handler(std::move(weak_handler_in)) {}
        void run() override;
    };

    HostResolver::SP                        _resolver;
    std::unique_ptr<SyncableThreadExecutor> _executor;
    static std::mutex                       _shared_lock;
    static AsyncResolver::SP                _shared_resolver;

    AsyncResolver(HostResolver::SP resolver, size_t num_threads);
public:
    void resolve_async(const vespalib::string &spec, ResultHandler::WP result_handler);
    void wait_for_pending_resolves();
    static AsyncResolver::SP create(Params params);
    static AsyncResolver::SP get_shared();
};

} // namespace vespalib
