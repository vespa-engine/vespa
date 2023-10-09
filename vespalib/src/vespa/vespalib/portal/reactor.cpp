// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reactor.h"
#include <cassert>

namespace vespalib::portal {

Reactor::EventHandler::~EventHandler() = default;

//-----------------------------------------------------------------------------

Reactor::Token::Token(Reactor &reactor, EventHandler &handler, int fd, bool read, bool write)
    : _reactor(reactor), _handler(handler), _fd(fd)
{
    ++_reactor._token_cnt;
    _reactor._selector.add(_fd, _handler, read, write);
}

void
Reactor::Token::update(bool read, bool write)
{
    _reactor._selector.update(_fd, _handler, read, write);
}

Reactor::Token::~Token()
{
    _reactor._selector.remove(_fd);
    _reactor.cancel_token(*this);
    --_reactor._token_cnt;
}

//-----------------------------------------------------------------------------

void
Reactor::cancel_token(const Token &)
{
    if (std::this_thread::get_id() == _thread.get_id()) {
        _skip_events = true;
    } else {
        std::unique_lock guard(_lock);
        size_t old_gen = _sync_seq;
        ++_wait_cnt;
        guard.unlock(); // UNLOCK
        _selector.wakeup();
        guard.lock(); // LOCK
        while (_sync_seq == old_gen) {
            _cond.wait(guard);
        }
        --_wait_cnt;
    }
}

void
Reactor::release_tokens()
{
    std::lock_guard guard(_lock);
    if (_wait_cnt > 0) {
        ++_sync_seq;
        _cond.notify_all();
    }
}

//-----------------------------------------------------------------------------

void
Reactor::handle_wakeup()
{
    _was_woken = true;
}

void
Reactor::handle_event(EventHandler &handler, bool read, bool write)
{
    if (!_skip_events) {
        handler.handle_event(read, write);
    }
}

void
Reactor::event_loop()
{
    while (!_done) {
        _selector.poll(_tick());
        _selector.dispatch(*this);
        if (_skip_events) {
            _skip_events = false;
        }
        if (_was_woken) {
            release_tokens();
            _was_woken = false;
        }
    }
}

//-----------------------------------------------------------------------------

Reactor::Reactor(std::function<int()> tick)
    : _selector(),
      _tick(std::move(tick)),
      _done(false),
      _was_woken(false),
      _skip_events(false),
      _lock(),
      _cond(),
      _sync_seq(0),
      _wait_cnt(0),
      _token_cnt(0),
      _thread(&Reactor::event_loop, this)
{
}

Reactor::~Reactor()
{
    assert(_token_cnt == 0);
    _done = true;
    _selector.wakeup();
    _thread.join();
}

Reactor::Token::UP
Reactor::attach(EventHandler &handler, int fd, bool read, bool write)
{
    return Token::UP(new Token(*this, handler, fd, read, write));
}

//-----------------------------------------------------------------------------

} // namespace vespalib::portal
