// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/fnet/frt/values.h>
#include <string>
#include <memory>
#include <algorithm>
#include <iosfwd>
#include <iterator>

namespace frtstream {

class ConnectionException{};
class InvokationException{
public:

    uint32_t errorCode;
    std::string errorMessage;
    InvokationException(uint32_t code, const std::string& msg ):
        errorCode(code), errorMessage(msg) {}
};

std::ostream& operator<<(std::ostream& s, const InvokationException& e);


class Method {
    std::string _name;
public:
    Method(const std::string& methodName) :
        _name(methodName) {}
    //implement in the future along with typechecking
    //Method(const std::string& name, const std::string& typeString);

    std::string name() const {
        return _name;
    }
};

class FrtStream {
protected:
    virtual FRT_Values& in() = 0;
    virtual FRT_Value& nextOut() = 0;

public:
    virtual ~FrtStream() {}

#define _FRTSTREAM_INTOPERATOR(bits) \
    FrtStream& operator<<(uint##bits##_t); \
    FrtStream& operator>>(uint##bits##_t&); \
    FrtStream& operator<<(int##bits##_t val) { \
        operator<<(static_cast<uint##bits##_t>(val)); \
        return *this; \
    } \
    FrtStream& operator>>(int##bits##_t &val) { \
        uint##bits##_t temp; \
        *this>>temp; \
        val = static_cast<int##bits##_t>(val); \
        return *this; \
    }

    _FRTSTREAM_INTOPERATOR(8);
    _FRTSTREAM_INTOPERATOR(16);
    _FRTSTREAM_INTOPERATOR(32);
    _FRTSTREAM_INTOPERATOR(64);
#undef _FRTSTREAM_INTOPERATOR

    FrtStream& operator<<(float);
    FrtStream& operator>>(float&);

    FrtStream& operator<<(double);
    FrtStream& operator>>(double&);

    FrtStream& operator<<(const std::string &str);
    FrtStream& operator>>(std::string &str);

    FrtStream& operator<<(std::string &str);

    template <template<typename, typename> class CONT, class T, class ALLOC>
    FrtStream& operator<<( const CONT<T, ALLOC> & cont );

    template <template<typename, typename> class CONT, class T, class ALLOC>
    FrtStream& operator>>( CONT<T, ALLOC> & cont );
};


} //end namespace frtstream


#include "frtstreamTemplateImp.hpp"

