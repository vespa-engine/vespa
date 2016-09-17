// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <boost/array.hpp>
#include <boost/version.hpp>
#include <vespa/vespalib/util/exceptions.h>

namespace filedistribution {

class Backtrace {
    static const size_t _maxBacktraceSize = 200;
  public:
    boost::array<void*, _maxBacktraceSize> _frames;
    const size_t _size;

    Backtrace();
};

VESPA_DEFINE_EXCEPTION(FileDoesNotExistException, vespalib::Exception);


std::ostream& operator<<(std::ostream& stream, const Backtrace& backtrace);

} //namespace filedistribution

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

