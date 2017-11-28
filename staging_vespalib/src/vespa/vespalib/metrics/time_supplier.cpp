// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "time_supplier.h"
#include <stdlib.h>

namespace vespalib::metrics {

// these should never be used for anything:

TimeSupplier::TimeStamp
TimeSupplier::now_stamp() const
{ abort(); }

double
TimeSupplier::stamp_to_s(TimeStamp) const
{ abort(); }

TimeSupplier::TimeSupplier()
{ abort(); }

} // namespace vespalib::metrics
