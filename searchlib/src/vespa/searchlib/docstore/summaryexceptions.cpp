// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "summaryexceptions.h"
#include <vespa/fastos/file.h>

using vespalib::IoException;
using vespalib::make_string;

namespace search {

SummaryException::SummaryException(std::string_view msg,
                                   FastOS_FileInterface &file,
                                   std::string_view location)
    : IoException(make_string("%s : Failing file = '%s'. Reason given by OS = '%s'",
                              vespalib::string(msg).c_str(), file.GetFileName(), file.getLastErrorString().c_str()),
                  getErrorType(file.GetLastError()), location)
{ }

}
