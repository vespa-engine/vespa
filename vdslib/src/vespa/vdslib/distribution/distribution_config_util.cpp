// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distribution_config_util.h"
#include <vespa/vespalib/text/stringtokenizer.h>
#include <boost/lexical_cast.hpp>

namespace storage::lib {

std::vector<uint16_t> DistributionConfigUtil::getGroupPath(vespalib::stringref path) {
    vespalib::StringTokenizer st(path, ".", "");
    std::vector<uint16_t> result(st.size());
    for (uint32_t i=0, n=result.size(); i<n; ++i) {
        result[i] = boost::lexical_cast<uint16_t>(st[i]);
    }
    return result;
}

}
