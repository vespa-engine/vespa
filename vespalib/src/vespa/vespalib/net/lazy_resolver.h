// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "socket_address.h"
#include "socket_spec.h"
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <chrono>
#include <memory>
#include <mutex>
#include <map>

namespace vespalib {

/**
 * Component used to perform lazy re-resolving of host names. The goal
 * of this class is to allow applications to (re-)connect from within
 * a network thread without stalling everything due to slow dns
 * responses while still being able to pick up on dns changes
 * (eventually). The idea is that the make_address function is called
 * up front during configuration from a non-critical thread. It will
 * (potentially) perform an initial synchronous resolve and return an
 * Address object that can later be used to obtain a string-based
 * connect spec where any host names have been replaced by ip
 * addresses without blocking. The host names are resolved under the
 * assumption that they will be used to connect to a remote server.
 **/
class LazyResolver : public std::enable_shared_from_this<LazyResolver>
{
public:
    using resolve_host_t = std::function<vespalib::string(const vespalib::string &)>;
    using clock = std::chrono::steady_clock;
    using seconds = std::chrono::duration<double>;
    using SP = std::shared_ptr<LazyResolver>;

    struct Params
    {
        resolve_host_t resolve_host;
        seconds        max_result_age;
        seconds        max_resolve_time;
        Params();
    };

    class Host : public std::enable_shared_from_this<Host>
    {
    private:
        friend class LazyResolver;
        vespalib::string  _host_name;
        LazyResolver::SP  _resolver;
        std::mutex        _ip_lock;
        bool              _ip_pending;
        vespalib::string  _ip_address;
        clock::time_point _ip_updated;
        Host(const vespalib::string &host_name, LazyResolver::SP resolver,
             const vespalib::string &ip_address);
        void update_ip_address(const vespalib::string &ip_address);
    public:
        ~Host();
        using SP = std::shared_ptr<Host>;
        const vespalib::string &host_name() const { return _host_name; }
        vespalib::string resolve();
    };

    class Address
    {
    private:
        friend class LazyResolver;
        vespalib::string _spec;
        Host::SP         _host;
        Address(const vespalib::string &spec, Host::SP host)
            : _spec(spec), _host(std::move(host)) {}
    public:
        using SP = std::shared_ptr<Address>;
        const vespalib::string &spec() const { return _spec; }
        vespalib::string resolve();
    };

private:
    struct UpdateTask : Executor::Task {
        LazyResolver &resolver;
        std::weak_ptr<Host> weak_host;
        UpdateTask(LazyResolver &resolver_in, std::weak_ptr<Host> weak_host_in)
            : resolver(resolver_in), weak_host(std::move(weak_host_in)) {}
        void run() override;
    };

    std::mutex                                       _host_lock;
    std::map<vespalib::string, std::weak_ptr<Host> > _host_map;
    Params                                           _params;
    ThreadStackExecutor                              _executor;
    LazyResolver(Params params);
    Host::SP try_lookup_host(const vespalib::string &host_name,
                             const std::lock_guard<std::mutex> &guard);
    Host::SP try_lookup_host(const vespalib::string &host_name);
    Host::SP insert_host(const vespalib::string &host_name, const vespalib::string &ip_address);
    vespalib::string resolve_host_now(const vespalib::string &host_name);
    bool should_request_update(clock::time_point ip_updated);
    bool try_request_update(std::weak_ptr<Host> self);
public:
    ~LazyResolver();
    void wait_for_pending_updates() { _executor.sync(); }
    Host::SP make_host(const vespalib::string &host_name);
    Address::SP make_address(const vespalib::string &spec);
    static vespalib::string default_resolve_host(const vespalib::string &host_name);
    static SP create(Params params = Params());
};

} // namespace vespalib
