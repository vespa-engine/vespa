// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "parsing_failed_exception.h"
#include <vespa/document/base/exceptions.h>

namespace document::select {

VESPA_IMPLEMENT_EXCEPTION(ParsingFailedException, vespalib::Exception);

}