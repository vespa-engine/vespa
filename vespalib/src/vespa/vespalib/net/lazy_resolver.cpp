// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lazy_resolver.h"
#include "socket_spec.h"

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.net.lazy_resolver");

namespace vespalib {

VESPA_THREAD_STACK_TAG(lazy_resolver_executor_thread);

LazyResolver::Params::Params()
    : resolve_host(default_resolve_host),
      max_result_age(seconds(300.0)),
      max_resolve_time(seconds(1.0))
{
}

//-----------------------------------------------------------------------------

LazyResolver::Host::Host(const vespalib::string &host_name, LazyResolver::SP resolver,
                         const vespalib::string &ip_address)
    : _host_name(host_name),
      _resolver(std::move(resolver)),
      _ip_lock(),
      _ip_pending(false),
      _ip_address(ip_address),
      _ip_updated(clock::now())
{
}

void
LazyResolver::Host::update_ip_address(const vespalib::string &ip_address)
{
    std::lock_guard<std::mutex> guard(_ip_lock);
    _ip_pending = false;
    _ip_address = ip_address;
    _ip_updated = clock::now();
}

LazyResolver::Host::~Host()
{
    // clean up weak_ptr to this
    _resolver->try_lookup_host(_host_name);
}

vespalib::string
LazyResolver::Host::resolve()
{
    std::lock_guard<std::mutex> guard(_ip_lock);
    if (!_ip_pending && _resolver->should_request_update(_ip_updated)) {
        // TODO(havardpe): switch to weak_from_this() when available
        _ip_pending = _resolver->try_request_update(shared_from_this());
    }
    return _ip_address;
}

//-----------------------------------------------------------------------------

vespalib::string
LazyResolver::Address::resolve()
{
    if (_host) {
        return SocketSpec(_spec).replace_host(_host->resolve()).spec();
    }
    return _spec;
}

//-----------------------------------------------------------------------------

void
LazyResolver::UpdateTask::run()
{
    if (Host::SP host = weak_host.lock()) {
        host->update_ip_address(resolver.resolve_host_now(host->host_name()));
    }
}

//-----------------------------------------------------------------------------

LazyResolver::LazyResolver(Params params)
    : _host_lock(),
      _host_map(),
      _params(std::move(params)),
      _executor(1, 128*1024, lazy_resolver_executor_thread, 4096)
{
}

LazyResolver::Host::SP
LazyResolver::try_lookup_host(const vespalib::string &host_name,
                              const std::lock_guard<std::mutex> &guard)
{
    (void) guard;
    auto pos = _host_map.find(host_name);
    if (pos != _host_map.end()) {
        Host::SP host = pos->second.lock();
        if (host) {
            return host;
        } else {
            _host_map.erase(pos);
        }
    }
    return Host::SP(nullptr);
}

LazyResolver::Host::SP
LazyResolver::try_lookup_host(const vespalib::string &host_name)
{
    std::lock_guard<std::mutex> guard(_host_lock);
    return try_lookup_host(host_name, guard);
}

LazyResolver::Host::SP
LazyResolver::insert_host(const vespalib::string &host_name, const vespalib::string &ip_address)
{
    std::lock_guard<std::mutex> guard(_host_lock);
    Host::SP host = try_lookup_host(host_name, guard);
    if (!host) {
        host.reset(new Host(host_name, shared_from_this(), ip_address));
        _host_map.emplace(host_name, host);
    }
    return host;
}

vespalib::string
LazyResolver::resolve_host_now(const vespalib::string &host_name)
{
    auto before = clock::now();
    vespalib::string ip_address = _params.resolve_host(host_name);
    seconds resolve_time = (clock::now() - before);
    if (resolve_time >= _params.max_resolve_time) {
        LOG(warning, "slow resolve time: '%s' -> '%s' (%g s)",
            host_name.c_str(), ip_address.c_str(), resolve_time.count());
    }
    if (ip_address.empty()) {
        LOG(warning, "could not resolve host name: '%s'", host_name.c_str());
    }
    return ip_address;
}

bool
LazyResolver::should_request_update(clock::time_point ip_updated)
{
    seconds result_age = (clock::now() - ip_updated);
    return (result_age >= _params.max_result_age);
}

bool
LazyResolver::try_request_update(std::weak_ptr<Host> self)
{
    Executor::Task::UP task(new UpdateTask(*this, std::move(self)));
    auto rejected = _executor.execute(std::move(task));
    return !rejected;
}

//-----------------------------------------------------------------------------

LazyResolver::~LazyResolver()
{
    _executor.shutdown().sync();
}

LazyResolver::Host::SP
LazyResolver::make_host(const vespalib::string &host_name)
{
    if (host_name.empty()) {
        return Host::SP(nullptr);
    }
    Host::SP host = try_lookup_host(host_name);
    if (host) {
        return host;
    }
    vespalib::string ip_address = resolve_host_now(host_name);
    if (ip_address == host_name) {
        return Host::SP(nullptr);
    }
    return insert_host(host_name, ip_address);
}

LazyResolver::Address::SP
LazyResolver::make_address(const vespalib::string &spec_str)
{
    SocketSpec spec(spec_str);
    if (!spec.valid()) {
        LOG(warning, "invalid socket spec: '%s'\n", spec_str.c_str());
    }
    return Address::SP(new Address(spec_str, make_host(spec.host())));
}

//-----------------------------------------------------------------------------

vespalib::string
LazyResolver::default_resolve_host(const vespalib::string &host_name)
{
    return SocketAddress::select_remote(80, host_name.c_str()).ip_address();
}

std::shared_ptr<LazyResolver>
LazyResolver::create(Params params)
{
    return std::shared_ptr<LazyResolver>(new LazyResolver(std::move(params)));
}

} // namespace vespalib
