// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <stdexcept>
#include <string>

namespace logdemon {

class MsgException : public std::exception {
private:
    std::string _string;
public:
    MsgException(const std::string & s) : _string(s) {}
    ~MsgException() override {}
    const char *what() const noexcept override { return _string.c_str(); }
};

class ConnectionException : public MsgException
{
public:
    ConnectionException(const std::string& msg) : MsgException(msg) {}
};

class DecodeException : public MsgException {
public:
    DecodeException(const std::string& msg) : MsgException(msg) {}
};

class SigTermException : public MsgException
{
public:
    SigTermException(const char *s) : MsgException(s) {}
};

class SomethingBad : public MsgException
{
public:
    SomethingBad(const char *s) : MsgException(s) {}
};

}
