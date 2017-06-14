// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <stdexcept>
#include <vespa/vespalib/stllike/string.h>

namespace logdemon {

class MsgException : public std::exception {
private:
    vespalib::string _string;
public:
    MsgException(const char *s) : _string(s) {}
    virtual ~MsgException() throw() {}
    const char *what() const throw() override { return _string.c_str(); }
};

class ConnectionException : public MsgException
{
public:
    ConnectionException(const char *s) : MsgException(s) {}
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

} // namespace
