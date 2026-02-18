// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport.h"

#include "iocomponent.h"
#include "transport_thread.h"

#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/rendezvous.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/vespalib/util/rendezvous.hpp>

#include <xxhash.h>

#include <vespa/log/log.h>
LOG_SETUP(".fnet.transport");

namespace {

struct HashState {
    using clock = std::chrono::high_resolution_clock;

    const void*       self;
    clock::time_point now;
    uint64_t          key_hash;
    HashState(const void* key, size_t key_len) __attribute__((noinline));
};

HashState::HashState(const void* key, size_t key_len)
    : self(this), now(clock::now()), key_hash(XXH64(key, key_len, 0)) {}

VESPA_THREAD_STACK_TAG(fnet_work_pool);

struct DefaultTimeTools : fnet::TimeTools {
    vespalib::duration    event_timeout() const override { return FNET_Scheduler::tick_ms; }
    vespalib::steady_time current_time() const override { return vespalib::steady_clock::now(); }
};

struct DebugTimeTools : fnet::TimeTools {
    vespalib::duration                     my_event_timeout;
    std::function<vespalib::steady_time()> my_current_time;
    DebugTimeTools(vespalib::duration d, std::function<vespalib::steady_time()> f) noexcept
        : my_event_timeout(d), my_current_time(std::move(f)) {}
    vespalib::duration    event_timeout() const override { return my_event_timeout; }
    vespalib::steady_time current_time() const override { return my_current_time(); }
};

struct CaptureMeet : vespalib::Rendezvous<int, bool> {
    using SP = std::shared_ptr<CaptureMeet>;
    vespalib::SyncableThreadExecutor& work_pool;
    vespalib::AsyncResolver&          async_resolver;
    std::function<bool()>             capture_hook;
    CaptureMeet(size_t N, vespalib::SyncableThreadExecutor& work_pool_in, vespalib::AsyncResolver& resolver_in,
                std::function<bool()> capture_hook_in)
        : vespalib::Rendezvous<int, bool>(N),
          work_pool(work_pool_in),
          async_resolver(resolver_in),
          capture_hook(std::move(capture_hook_in)) {}
    void mingle() override {
        work_pool.sync();
        async_resolver.wait_for_pending_resolves();
        bool result = capture_hook();
        for (size_t i = 0; i < size(); ++i) {
            out(i) = result;
        }
    }
};

struct CaptureTask : FNET_Task {
    CaptureMeet::SP meet;
    CaptureTask(FNET_Scheduler* scheduler, CaptureMeet::SP meet_in)
        : FNET_Task(scheduler), meet(std::move(meet_in)) {}
    void PerformTask() override {
        int dummy_value = 0; // rendezvous must have input value
        if (meet->rendezvous(dummy_value)) {
            ScheduleNow();
        } else {
            delete this;
        }
    };
};

} // namespace

namespace fnet {

TimeTools::SP TimeTools::make_debug(
    vespalib::duration event_timeout, std::function<vespalib::steady_time()> current_time) {
    return std::make_shared<DebugTimeTools>(event_timeout, std::move(current_time));
}

TransportConfig::TransportConfig(int num_threads)
    : _config(), _resolver(), _crypto(), _time_tools(), _num_threads(num_threads) {}

TransportConfig::~TransportConfig() = default;

vespalib::AsyncResolver::SP TransportConfig::resolver() const {
    return _resolver ? _resolver : vespalib::AsyncResolver::get_shared();
}

vespalib::CryptoEngine::SP TransportConfig::crypto() const {
    return _crypto ? _crypto : vespalib::CryptoEngine::get_default();
}

fnet::TimeTools::SP TransportConfig::time_tools() const {
    return _time_tools ? _time_tools : std::make_shared<DefaultTimeTools>();
}

} // namespace fnet

void FNET_Transport::wait_for_pending_resolves() { _async_resolver->wait_for_pending_resolves(); }

FNET_Transport::FNET_Transport(const fnet::TransportConfig& cfg)
    : _async_resolver(cfg.resolver()),
      _crypto_engine(cfg.crypto()),
      _time_tools(cfg.time_tools()),
      _work_pool(std::make_unique<vespalib::ThreadStackExecutor>(1, fnet_work_pool, 1024)),
      _threads(),
      _pool(),
      _config(cfg.config()) {
    // TODO Temporary logging to track down overspend
    LOG(debug, "FNET_Transport threads=%d from :%s", cfg.num_threads(), vespalib::getStackTrace(0).c_str());
    assert(cfg.num_threads() >= 1);
    for (size_t i = 0; i < cfg.num_threads(); ++i) {
        _threads.emplace_back(std::make_unique<FNET_TransportThread>(*this));
    }
}

FNET_Transport::~FNET_Transport() { _pool.join(); }

void FNET_Transport::post_or_perform(vespalib::Executor::Task::UP task) {
    if (auto rejected = _work_pool->execute(std::move(task))) {
        rejected->run();
    }
}

void FNET_Transport::resolve_async(
    const std::string& spec, vespalib::AsyncResolver::ResultHandler::WP result_handler) {
    _async_resolver->resolve_async(spec, std::move(result_handler));
}

vespalib::CryptoSocket::UP FNET_Transport::create_client_crypto_socket(
    vespalib::SocketHandle socket, const vespalib::SocketSpec& spec) {
    return _crypto_engine->create_client_crypto_socket(std::move(socket), spec);
}

vespalib::CryptoSocket::UP FNET_Transport::create_server_crypto_socket(vespalib::SocketHandle socket) {
    return _crypto_engine->create_server_crypto_socket(std::move(socket));
}

FNET_TransportThread* FNET_Transport::select_thread(const void* key, size_t key_len) const {
    HashState hash_state(key, key_len);
    size_t    hash_value = XXH64(&hash_state, sizeof(hash_state), 0);
    size_t    thread_id = (hash_value % _threads.size());
    return _threads[thread_id].get();
}

FNET_Connector* FNET_Transport::Listen(
    const char* spec, FNET_IPacketStreamer* streamer, FNET_IServerAdapter* serverAdapter) {
    return select_thread(spec, strlen(spec))->Listen(spec, streamer, serverAdapter);
}

FNET_Connection* FNET_Transport::Connect(
    const char* spec, FNET_IPacketStreamer* streamer, FNET_IServerAdapter* serverAdapter, FNET_Context connContext) {
    return select_thread(spec, strlen(spec))->Connect(spec, streamer, serverAdapter, connContext);
}

uint32_t FNET_Transport::GetNumIOComponents() {
    uint32_t result = 0;
    for (const auto& thread : _threads) {
        result += thread->GetNumIOComponents();
    }
    return result;
}

void FNET_Transport::sync() {
    for (const auto& thread : _threads) {
        thread->sync();
    }
}

void FNET_Transport::detach(FNET_IServerAdapter* server_adapter) {
    for (const auto& thread : _threads) {
        thread->init_detach(server_adapter);
    }
    wait_for_pending_resolves();
    sync();
    for (const auto& thread : _threads) {
        thread->fini_detach(server_adapter);
    }
    sync();
}

FNET_Scheduler* FNET_Transport::GetScheduler() { return select_thread(nullptr, 0)->GetScheduler(); }

bool FNET_Transport::execute(FNET_IExecutable* exe) { return select_thread(nullptr, 0)->execute(exe); }

void FNET_Transport::ShutDown(bool waitFinished) {
    for (const auto& thread : _threads) {
        thread->ShutDown(waitFinished);
    }
    if (waitFinished) {
        wait_for_pending_resolves();
        _work_pool->shutdown().sync();
    }
}

void FNET_Transport::WaitFinished() {
    for (const auto& thread : _threads) {
        thread->WaitFinished();
    }
    wait_for_pending_resolves();
    _work_pool->shutdown().sync();
}

bool FNET_Transport::Start() {
    for (const auto& thread : _threads) {
        thread->Start(_pool);
    }
    return true;
}

void FNET_Transport::attach_capture_hook(std::function<bool()> capture_hook) {
    auto meet =
        std::make_shared<CaptureMeet>(_threads.size(), *_work_pool, *_async_resolver, std::move(capture_hook));
    for (auto& thread : _threads) {
        // tasks will be deleted when the capture_hook returns false
        auto* task = new CaptureTask(thread->GetScheduler(), meet);
        task->ScheduleNow();
    }
}

void FNET_Transport::Add(FNET_IOComponent* comp, bool needRef) { comp->Owner()->Add(comp, needRef); }

void FNET_Transport::Close(FNET_IOComponent* comp, bool needRef) { comp->Owner()->Close(comp, needRef); }

void FNET_Transport::Main() {
    assert(_threads.size() == 1);
    _threads[0]->Main();
}
