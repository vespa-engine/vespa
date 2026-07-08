// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/frt/rpcrequest.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/signalshutdown.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/signalhandler.h>

#include <condition_variable>
#include <future>
#include <mutex>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("rpc_callback_server");

/**
 * Class keeping track of 'detached' threads in order to wait for
 * their completion on program shutdown. Threads are not actually
 * detached, but perform co-operative auto-joining on completion.
 **/
class AutoJoiner {
private:
    std::mutex              _lock;
    std::condition_variable _cond;
    bool                    _closed;
    size_t                  _pending;
    std::thread             _thread;
    struct JoinGuard {
        std::thread thread;
        ~JoinGuard() {
            if (thread.joinable()) {
                assert(std::this_thread::get_id() != thread.get_id());
                thread.join();
            }
        }
    };
    void notify_start() {
        std::lock_guard guard(_lock);
        if (!_closed) {
            ++_pending;
        } else {
            throw std::runtime_error("no new threads allowed");
        }
    }
    void notify_done(std::thread thread) {
        JoinGuard        join;
        std::unique_lock guard(_lock);
        join.thread = std::move(_thread);
        _thread = std::move(thread);
        if (--_pending == 0 && _closed) {
            _cond.notify_all();
        }
    }
    auto wrap_task(auto task, std::promise<std::thread>& promise) {
        return [future = promise.get_future(), task = std::move(task), &owner = *this]() mutable {
            auto thread = future.get();
            assert(std::this_thread::get_id() == thread.get_id());
            task();
            owner.notify_done(std::move(thread));
        };
    }

public:
    AutoJoiner() : _lock(), _cond(), _closed(false), _pending(0), _thread() {}
    ~AutoJoiner() { close_and_wait(); }
    void start(auto task) {
        notify_start();
        std::promise<std::thread> promise;
        promise.set_value(std::thread(wrap_task(std::move(task), promise)));
    };
    void close_and_wait() {
        JoinGuard        join;
        std::unique_lock guard(_lock);
        _closed = true;
        while (_pending > 0) {
            _cond.wait(guard);
        }
        std::swap(join.thread, _thread);
    }
};

AutoJoiner& auto_joiner() {
    static AutoJoiner obj;
    return obj;
}

struct RPC : public FRT_Invokable {
    void CallBack(FRT_RPCRequest* req);
    void Init(FRT_Supervisor* s);
};

void do_callback(FRT_RPCRequest* req) {
    FNET_Connection* conn = req->GetConnection();
    FRT_RPCRequest*  cb = new FRT_RPCRequest();
    cb->SetMethodName(req->GetParams()->GetValue(0)._string._str);
    FRT_Supervisor::InvokeSync(conn->Owner(), conn, cb, 5.0);
    if (cb->IsError()) {
        printf("[error(%d): %s]\n", cb->GetErrorCode(), cb->GetErrorMessage());
    }
    cb->internal_subref();
    req->Return();
}

void RPC::CallBack(FRT_RPCRequest* req) {
    req->Detach();
    auto_joiner().start([req] { do_callback(req); });
}

void RPC::Init(FRT_Supervisor* s) {
    FRT_ReflectionBuilder rb(s);
    //-------------------------------------------------------------------
    rb.DefineMethod("callBack", "s", "", FRT_METHOD(RPC::CallBack), this);
    //-------------------------------------------------------------------
}

class MyApp {
public:
    int main(int argc, char** argv);
    ~MyApp() { auto_joiner().close_and_wait(); }
};

int MyApp::main(int argc, char** argv) {
    FNET_SignalShutDown::hookSignals();
    if (argc < 2) {
        printf("usage  : rpc_server <listenspec>\n");
        return 1;
    }
    RPC                      rpc;
    fnet::frt::StandaloneFRT server;
    FRT_Supervisor&          supervisor = server.supervisor();
    rpc.Init(&supervisor);
    supervisor.Listen(argv[1]);
    FNET_SignalShutDown ssd(*supervisor.GetTransport());
    server.supervisor().GetTransport()->WaitFinished();
    return 0;
}

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    MyApp myapp;
    return myapp.main(argc, argv);
}
