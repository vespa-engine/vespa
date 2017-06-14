// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "result.h"

namespace mbus {

Result::Handover::Handover(bool a, const Error &e, Message *m)
    : _accepted(a),
      _error(e),
      _msg(m)
{ }

Result::Result()
    : _accepted(true),
      _error(),
      _msg()
{ }

Result::Result(const Error &err, Message::UP msg)
    : _accepted(false),
      _error(err),
      _msg(std::move(msg))
{ }

Result::Result(Result &&rhs)
    : _accepted(rhs._accepted),
      _error(rhs._error),
      _msg(std::move(rhs._msg))
{ }

Result::Result(const Handover &rhs)
    : _accepted(rhs._accepted),
      _error(rhs._error),
      _msg(rhs._msg)
{ }

Result::~Result() {}

bool
Result::isAccepted() const
{
    return _accepted;
}

const Error &
Result::getError() const
{
    return _error;
}

Message::UP
Result::getMessage()
{
    return std::move(_msg);
}

Result::operator Handover()
{
    return Handover(_accepted, _error, _msg.release());
}

Result &
Result::operator=(Result &&rhs)
{
    _accepted = rhs._accepted;
    _error = rhs._error;
    _msg = std::move(rhs._msg);
    return *this;
}

Result &
Result::operator=(const Handover &rhs)
{
    _accepted = rhs._accepted;
    _error = rhs._error;
    _msg.reset(rhs._msg);
    return *this;
}

} // namespace mbus
