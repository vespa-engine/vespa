// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "logf.h"
#include "bufferedlogger.h"

#if defined(__cpp_lib_format)

namespace ns_log {

void
do_fmt_log_impl(Logger& logger, Logger::LogLevel level, const char* file, int line,
                std::string_view fmt_str, std::format_args fmt_args)
{
    // TODO consider `vformat_to` with a custom, bounded output iterator to a stack buffer if
    //  we want to limit the number of allocs per log entry. But the existing legacy logging
    //  code is pretty allocation-happy as it is, so probably doesn't matter much in practice.

    // We expect that `fmt_str` has been compile-time checked, so we do not attempt to catch
    // `std::format_error` if it happens (we consider it an invariant violation). Let it bubble
    // up and (most likely) terminate the process instead.
    std::string buf = std::vformat(fmt_str, fmt_args);
    logger.doLogCore(logger.timer(), level, file, line, buf.data(), buf.size());
    ns_log::BufferedLogger::instance().trimCache(); // Symmetric with Logger::doLog()
}

}

#endif // defined(__cpp_lib_format)
