// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transport.h"
#include "transport_thread.h"
#include "iocomponent.h"
#include <chrono>
#include <xxhash.h>

namespace {

struct HashState {
    using clock = std::chrono::high_resolution_clock;

    const void       *self;
    clock::time_point now;
    uint64_t          key_hash;
    HashState(const void *key, size_t key_len)
        : self(this),
          now(clock::now()),
          key_hash(XXH64(key, key_len, 0)) {}
};

VESPA_THREAD_STACK_TAG(fnet_work_pool);

} // namespace <unnamed>

FNET_Transport::FNET_Transport(vespalib::AsyncResolver::SP resolver, vespalib::CryptoEngine::SP crypto, size_t num_threads)
    : _async_resolver(std::move(resolver)),
      _crypto_engine(std::move(crypto)),
      _work_pool(1, 128 * 1024, fnet_work_pool, 1024),
      _threads()
{
    assert(num_threads >= 1);
    for (size_t i = 0; i < num_threads; ++i) {
        _threads.emplace_back(new FNET_TransportThread(*this));
    }
}

FNET_Transport::~FNET_Transport() = default;

void
FNET_Transport::post_or_perform(vespalib::Executor::Task::UP task)
{
    if (auto rejected = _work_pool.execute(std::move(task))) {
        rejected->run();
    }
}

void
FNET_Transport::resolve_async(const vespalib::string &spec,
                              vespalib::AsyncResolver::ResultHandler::WP result_handler)
{
    _async_resolver->resolve_async(spec, std::move(result_handler));
}

vespalib::CryptoSocket::UP
FNET_Transport::create_client_crypto_socket(vespalib::SocketHandle socket, const vespalib::SocketSpec &spec)
{
    return _crypto_engine->create_client_crypto_socket(std::move(socket), spec);
}

vespalib::CryptoSocket::UP
FNET_Transport::create_server_crypto_socket(vespalib::SocketHandle socket)
{
    return _crypto_engine->create_server_crypto_socket(std::move(socket));
}

FNET_TransportThread *
FNET_Transport::select_thread(const void *key, size_t key_len) const
{
    HashState hash_state(key, key_len);
    size_t hash_value = XXH64(&hash_state, sizeof(hash_state), 0);
    size_t thread_id = (hash_value % _threads.size());
    return _threads[thread_id].get();
}

FNET_Connector *
FNET_Transport::Listen(const char *spec, FNET_IPacketStreamer *streamer,
                       FNET_IServerAdapter *serverAdapter)
{
    return select_thread(spec, strlen(spec))->Listen(spec, streamer, serverAdapter);
}

FNET_Connection *
FNET_Transport::Connect(const char *spec, FNET_IPacketStreamer *streamer,
                        FNET_IPacketHandler *adminHandler,
                        FNET_Context adminContext,
                        FNET_IServerAdapter *serverAdapter,
                        FNET_Context connContext)
{
    return select_thread(spec, strlen(spec))->Connect(spec, streamer, adminHandler, adminContext, serverAdapter, connContext);
}

uint32_t
FNET_Transport::GetNumIOComponents()
{
    uint32_t result = 0;
    for (const auto &thread: _threads) {
        result += thread->GetNumIOComponents();
    }
    return result;
}

void
FNET_Transport::SetIOCTimeOut(uint32_t ms)
{
    for (const auto &thread: _threads) {
        thread->SetIOCTimeOut(ms);
    }
}

void
FNET_Transport::SetMaxInputBufferSize(uint32_t bytes)
{
    for (const auto &thread: _threads) {
        thread->SetMaxInputBufferSize(bytes);
    }
}

void
FNET_Transport::SetMaxOutputBufferSize(uint32_t bytes)
{
    for (const auto &thread: _threads) {
        thread->SetMaxOutputBufferSize(bytes);
    }
}

void
FNET_Transport::SetTCPNoDelay(bool noDelay)
{
    for (const auto &thread: _threads) {
        thread->SetTCPNoDelay(noDelay);
    }
}

void
FNET_Transport::sync()
{
    for (const auto &thread: _threads) {
        thread->sync();
    }
}

FNET_Scheduler *
FNET_Transport::GetScheduler()
{
    return select_thread(nullptr, 0)->GetScheduler();
}

bool
FNET_Transport::execute(FNET_IExecutable *exe)
{
    return select_thread(nullptr, 0)->execute(exe);
}

void
FNET_Transport::ShutDown(bool waitFinished)
{
    for (const auto &thread: _threads) {
        thread->ShutDown(waitFinished);
    }
    if (waitFinished) {
        _async_resolver->wait_for_pending_resolves();
        _work_pool.shutdown().sync();
    }
}

void
FNET_Transport::WaitFinished()
{
    for (const auto &thread: _threads) {
        thread->WaitFinished();
    }
    _async_resolver->wait_for_pending_resolves();
    _work_pool.shutdown().sync();
}

bool
FNET_Transport::Start(FastOS_ThreadPool *pool)
{
    bool result = true;
    for (const auto &thread: _threads) {
        result &= thread->Start(pool);
    }
    return result;
}

void
FNET_Transport::Add(FNET_IOComponent *comp, bool needRef) {
    comp->Owner()->Add(comp, needRef);
}


void
FNET_Transport::Close(FNET_IOComponent *comp, bool needRef) {
    comp->Owner()->Close(comp, needRef);
}

void
FNET_Transport::Main() {
    assert(_threads.size() == 1);
    _threads[0]->Main();
}
