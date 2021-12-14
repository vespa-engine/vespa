// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.hpp>
#include <ostream>
#include <istream>

namespace vespalib {

const stringref::size_type stringref::npos;

stringref::size_type
stringref::rfind(const char * s, size_type e) const {
    size_type n = strlen(s);
    if (n <= size()) {
        size_type sz = std::min(size()-n, e);
        const char *b = begin();
        do {
            if (s[0] == b[sz]) {
                bool found(true);
                for(size_t i(1); found && (i < n); i++) {
                    found = s[i] == b[sz+i];
                }
                if (found) {
                    return sz;
                }
            }
        } while (sz-- > 0);
    }
    return npos;
}

stringref::size_type
stringref::find(stringref s, size_type start) const {
    const char *buf = begin()+start;
    const char *e = end() - s.size();
    while (buf <= e) {
        size_t i(0);
        for (; (i < s.size()) && (buf[i] == s[i]); i++);
        if (i == s.size()) {
            return buf - begin();
        } else {
            buf++;
        }
    }
    return npos;
}

std::ostream & operator << (std::ostream & os, stringref v)
{
    return os.write(v.data(), v.size());
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

template std::ostream & operator << (std::ostream & os, const string & v);
template std::istream & operator >> (std::istream & is, string & v);

string
operator + (stringref a, const char * b)
{
    string t(a);
    t += b;
    return t;
}

string
operator + (const char * a, stringref b)
{
    string t(a);
    t += b;
    return t;
}

string
operator + (stringref a, stringref b)
{
    string t(a);
    t += b;
    return t;
}

template class small_string<48>;

template string operator + (const string & a, const string & b);
template string operator + (const string & a, stringref b);
template string operator + (stringref a, const string & b);
template string operator + (const string & a, const char * b);
template string operator + (const  char * a, const string & b);

const string &empty_string() {
    static string empty;
    return empty;
}

}
