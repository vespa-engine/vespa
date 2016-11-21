// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/stllike/string.hpp>
#include <ostream>
#include <istream>

namespace vespalib {

const stringref::size_type stringref::npos;

std::ostream & operator << (std::ostream & os, const stringref & v)
{
    return os.write(v.c_str(), v.size());
}

template<uint32_t SS>
std::ostream & operator << (std::ostream & os, const small_string<SS> & v)
{
     return os << v.buffer();
}

template<uint32_t SS>
std::istream & operator >> (std::istream & is, small_string<SS> & v)
{
    std::string s;
    is >> s;
    v = s;
    return is;
}

template std::ostream & operator << (std::ostream & os, const vespalib::string & v);
template std::istream & operator >> (std::istream & is, vespalib::string & v);

vespalib::string
operator + (const vespalib::stringref & a, const char * b)
{
    vespalib::string t(a);
    t += b;
    return t;
}

vespalib::string
operator + (const char * a, const vespalib::stringref & b)
{
    vespalib::string t(a);
    t += b;
    return t;
}

vespalib::string
operator + (const vespalib::stringref & a, const vespalib::stringref & b)
{
    vespalib::string t(a);
    t += b;
    return t;
}

template class small_string<48>;

}
