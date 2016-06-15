// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".bitdecode64");
#include "bitencode64.h"
#include "bitdecode64.h"


namespace search
{

namespace fakedata
{

template class BitDecode64<true>;

template class BitDecode64<false>;

} // namespace fakedata

} // namespace search
