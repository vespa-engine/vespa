// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "async_resolver.h"
#include "socket_spec.h"
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.async_resolver");

namespace vespalib {

VESPA_THREAD_STACK_TAG(async_resolver_executor_thread);

//-----------------------------------------------------------------------------

AsyncResolver::time_point
AsyncResolver::SteadyClock::now()
{
    return std::chrono::steady_clock::now();
}

//-----------------------------------------------------------------------------

vespalib::string
AsyncResolver::SimpleHostResolver::ip_address(const vespalib::string &host_name)
{
    return SocketAddress::select_remote(80, host_name.c_str()).ip_address();
}

//-----------------------------------------------------------------------------

AsyncResolver::Params::Params()
    : clock(std::make_shared<SteadyClock>()),
      resolver(std::make_shared<SimpleHostResolver>()),
      max_cache_size(10000),
      max_result_age(60.0),
      max_resolve_time(1.0),
      num_threads(4)
{
}

//-----------------------------------------------------------------------------

vespalib::string
AsyncResolver::LoggingHostResolver::ip_address(const vespalib::string &host_name)
{
    auto before = _clock->now();
    vespalib::string ip_address = _resolver->ip_address(host_name);
    seconds resolve_time = (_clock->now() - before);
    if (resolve_time >= _max_resolve_time) {
        LOG(warning, "slow resolve time: '%s' -> '%s' (%g s)",
            host_name.c_str(), ip_address.c_str(), resolve_time.count());
    }
    if (ip_address.empty()) {
        LOG(warning, "could not resolve host name: '%s'", host_name.c_str());
    }
    return ip_address;
}

//-----------------------------------------------------------------------------

bool
AsyncResolver::CachingHostResolver::should_evict_oldest_entry(const std::lock_guard<std::mutex> &, time_point now)
{
    if (_queue.empty()) {
        return false;
    }
    if (_queue.size() > _max_cache_size) {
        return true;
    }
    return (_queue.front()->second.end_time <= now);
}

bool
AsyncResolver::CachingHostResolver::lookup(const vespalib::string &host_name, vespalib::string &ip_address)
{
    auto now = _clock->now();
    std::lock_guard<std::mutex> guard(_lock);
    while (should_evict_oldest_entry(guard, now)) {
        _map.erase(_queue.front());
        _queue.pop();
    }
    assert(_map.size() == _queue.size());
    auto pos = _map.find(host_name);
    if (pos != _map.end()) {
        ip_address = pos->second.ip_address;
        return true;
    }
    return false;
}

void
AsyncResolver::CachingHostResolver::store(const vespalib::string &host_name, const vespalib::string &ip_address)
{
    auto end_time = _clock->now() + std::chrono::duration_cast<time_point::duration>(_max_result_age);
    std::lock_guard<std::mutex> guard(_lock);
    auto res = _map.emplace(host_name, Entry(ip_address, end_time));
    if (res.second) {
        _queue.push(res.first);
    }
    assert(_map.size() == _queue.size());
}

AsyncResolver::CachingHostResolver::CachingHostResolver(Clock::SP clock, HostResolver::SP resolver, size_t max_cache_size, seconds max_result_age) noexcept
    : _clock(std::move(clock)),
      _resolver(std::move(resolver)),
      _max_cache_size(max_cache_size),
      _max_result_age(max_result_age),
      _lock(),
      _map(),
      _queue()
{
}

vespalib::string
AsyncResolver::CachingHostResolver::ip_address(const vespalib::string &host_name)
{
    vespalib::string ip_address;
    if (lookup(host_name, ip_address)) {
        return ip_address;
    }
    ip_address = _resolver->ip_address(host_name);
    if (ip_address != host_name) {
        store(host_name, ip_address);
    }
    return ip_address;
}

//-----------------------------------------------------------------------------

void
AsyncResolver::ResolveTask::run()
{
    if (ResultHandler::SP handler = weak_handler.lock()) {        
        SocketSpec socket_spec(spec);
        if (!socket_spec.valid()) {
            LOG(warning, "invalid socket spec: '%s'", spec.c_str());
        }
        if (!socket_spec.host().empty()) {
            socket_spec = socket_spec.replace_host(resolver.ip_address(socket_spec.host()));
        }
        handler->handle_result(socket_spec.client_address());
    }
}

//-----------------------------------------------------------------------------

std::mutex AsyncResolver::_shared_lock;
AsyncResolver::SP AsyncResolver::_shared_resolver(nullptr);

AsyncResolver::AsyncResolver(HostResolver::SP resolver, size_t num_threads)
    : _resolver(std::move(resolver)),
      _executor(std::make_unique<ThreadStackExecutor>(num_threads, 128_Ki, async_resolver_executor_thread))
{
}

void
AsyncResolver::wait_for_pending_resolves() {
    _executor->sync();
}

void
AsyncResolver::resolve_async(const vespalib::string &spec, ResultHandler::WP result_handler)
{
    auto task = std::make_unique<ResolveTask>(spec, *_resolver, std::move(result_handler));
    auto rejected = _executor->execute(std::move(task));
    assert(!rejected);
}

AsyncResolver::SP
AsyncResolver::create(Params params)
{
    auto logger = std::make_shared<LoggingHostResolver>(params.clock, std::move(params.resolver), params.max_resolve_time);
    auto cacher = std::make_shared<CachingHostResolver>(std::move(params.clock), std::move(logger), params.max_cache_size, params.max_result_age);
    return SP(new AsyncResolver(std::move(cacher), params.num_threads));
}

AsyncResolver::SP
AsyncResolver::get_shared()
{
    std::lock_guard<std::mutex> guard(_shared_lock);
    if (!_shared_resolver) {
        _shared_resolver = create(Params());
    }
    return _shared_resolver;
}

//-----------------------------------------------------------------------------

} // namespace vespalib
