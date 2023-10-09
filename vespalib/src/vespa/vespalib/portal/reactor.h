// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/selector.h>

#include <atomic>
#include <condition_variable>
#include <functional>
#include <memory>
#include <mutex>
#include <thread>

namespace vespalib::portal {

class Reactor
{
public:
    struct EventHandler {
        virtual void handle_event(bool read, bool write) = 0;
        virtual ~EventHandler();
    };
    friend class Selector<EventHandler>;
    class Token {
        friend class Reactor;
    private:
        Reactor &_reactor;
        EventHandler &_handler;
        int _fd;
        Token(const Token &) = delete;
        Token &operator=(const Token &) = delete;
        Token(Token &&) = delete;
        Token &operator=(Token &&) = delete;
        Token(Reactor &reactor, EventHandler &handler, int fd, bool read, bool write);
    public:
        using UP = std::unique_ptr<Token>;
        void update(bool read, bool write);
        ~Token();
    };

private:
    Selector<EventHandler>  _selector;
    std::function<int()>    _tick;
    std::atomic<bool>       _done;
    bool                    _was_woken;
    bool                    _skip_events;
    std::mutex              _lock;
    std::condition_variable _cond;
    size_t                  _sync_seq;
    size_t                  _wait_cnt;
    std::atomic<size_t>     _token_cnt;
    std::thread             _thread;

    void cancel_token(const Token &token);
    void release_tokens();

    void handle_wakeup();
    void handle_event(EventHandler &handler, bool read, bool write);
    void event_loop();

public:
    Reactor(std::function<int()> tick);
    Reactor() : Reactor([]() noexcept { return -1; }) {}
    ~Reactor();
    Token::UP attach(EventHandler &handler, int fd, bool read, bool write);
};

} // namespace vespalib::portal
