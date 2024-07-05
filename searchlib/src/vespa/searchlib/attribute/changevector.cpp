// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "changevector.hpp"
#include "stringbase.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.changevector");

namespace search {

StringChangeData::StringChangeData(const vespalib::string & s)
    : _s(s)
{
    if (StringAttribute::countZero(s.data(), s.size()) > 0) {
        LOG(warning,
            "StringChangeData(): "
            "Input string contains <null> byte(s); "
            "truncating. (ticket #3079131)");
        _s.assign(s.data()); // keep data up to (not including) first '\0' byte
    }
}

template class ChangeVectorT<ChangeTemplate<StringChangeData>>;
template class ChangeVectorT<ChangeTemplate<NumericChangeData<int64_t>>>;
template class ChangeVectorT<ChangeTemplate<NumericChangeData<double>>>;

}
