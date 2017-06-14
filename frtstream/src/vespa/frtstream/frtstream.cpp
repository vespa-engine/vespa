// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "frtstream.h"
#include <algorithm>
#include <ostream>

using namespace fnet;

namespace frtstream {



#define _FRTSTREAM_INTEGER_OPERATOR(bits) \
FrtStream& FrtStream::operator<<(uint##bits##_t i) { \
    in().AddInt##bits(i); \
    return *this; \
} \
FrtStream& FrtStream::operator>>(uint##bits##_t &i) { \
    i = nextOut()._intval##bits; \
    return *this; \
}

_FRTSTREAM_INTEGER_OPERATOR(8);
_FRTSTREAM_INTEGER_OPERATOR(16);
_FRTSTREAM_INTEGER_OPERATOR(32);
_FRTSTREAM_INTEGER_OPERATOR(64);
#undef _FRTSTREAM_INTEGER_OPERATOR

#define _FRTSTREAM_FLOAT_OPERATOR(floatType, floatTypeCapitalized) \
FrtStream& FrtStream::operator<<(floatType i) { \
    in().Add##floatTypeCapitalized(i); \
    return *this; \
} \
FrtStream& FrtStream::operator>>(floatType &i) { \
    i = nextOut()._##floatType; \
    return *this; \
}

_FRTSTREAM_FLOAT_OPERATOR(float, Float);
_FRTSTREAM_FLOAT_OPERATOR(double, Double);
#undef _FRTSTREAM_FLOAT_OPERATOR

FrtStream& FrtStream::operator<<(const std::string &str) {
    in().AddString(str.c_str());
    return *this;
}

FrtStream& FrtStream::operator>>(std::string &str) {
    str = nextOut()._string._str;
    return *this;
}


std::ostream& operator<<(std::ostream& s, const InvokationException& e) {
    s <<"InvocationException: " <<std::endl
      <<"ErrorCode: " <<e.errorCode <<std::endl
      <<"ErrorMessage: " <<e.errorMessage;
    return s;
}




} //end namespace frtstream
