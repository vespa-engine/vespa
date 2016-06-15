// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <boost/exception/all.hpp>
#include <boost/current_function.hpp>
#include <boost/exception/all.hpp>
#include <boost/array.hpp>
#include <boost/version.hpp>

namespace filedistribution {

class Backtrace {
    static const size_t _maxBacktraceSize = 200;
  public:
    boost::array<void*, _maxBacktraceSize> _frames;
    const size_t _size;

    Backtrace();
};


std::ostream& operator<<(std::ostream& stream, const Backtrace& backtrace);

namespace errorinfo {
typedef boost::error_info<struct tag_Backtrace, Backtrace> Backtrace;
typedef boost::error_info<struct tag_UserMessage, Backtrace> ExplanationForUser;
}

//Exceptions should inherit virtually from boost and std exception,
//see http://www.boost.org/doc/libs/1_39_0/libs/exception/doc/using_virtual_inheritance_in_exception_types.html
struct Exception : virtual boost::exception, virtual std::exception {
    Exception() {
        *this << errorinfo::Backtrace(Backtrace());
    }
};

} //namespace filedistribution

#if BOOST_VERSION < 103700
#define BOOST_THROW_EXCEPTION(x)\
    ::boost::throw_exception( ::boost::enable_error_info(x) <<          \
                             ::boost::throw_function(BOOST_CURRENT_FUNCTION) << \
                             ::boost::throw_file(__FILE__) <<           \
                             ::boost::throw_line((int)__LINE__) )

#endif


//********** Begin: Please remove when fixed upstream.
//boost 1.36 & 1.37 bugfix: allow attaching a boost::filesytem::path to a boost::exception
//using the error info mechanism.
#include <boost/filesystem/path.hpp>

namespace boost{
namespace to_string_detail {
std::basic_ostream<boost::filesystem::path::string_type::value_type,
                   boost::filesystem::path::string_type::traits_type > &
operator<<
( std::basic_ostream<boost::filesystem::path::string_type::value_type,
                     boost::filesystem::path::string_type::traits_type >& os, const boost::filesystem::path & ph );
}
}

//********** End:   Please remove when fixed upstream.

