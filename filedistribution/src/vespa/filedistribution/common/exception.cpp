// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "exception.h"

#include <execinfo.h>

std::ostream&
filedistribution::operator<<(std::ostream& stream, const Backtrace& backtrace) {
    char** strings = backtrace_symbols(
            &*backtrace._frames.begin(), backtrace._size);

    stream <<"Backtrace:" <<std::endl;
    for (size_t i = 0; i<backtrace._size; ++i) {
        stream <<strings[i] <<std::endl;
    }

    free(strings);
    return stream;
}


filedistribution::Backtrace::Backtrace()
    :_size(backtrace(&*_frames.begin(), _frames.size()))
{}
