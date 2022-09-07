// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ranking_expressions.h"
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/data/lz4_input_decoder.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.ranking_expressions");

namespace proton::matching {

namespace {

vespalib::string extract_data(vespalib::Input &input) {
    vespalib::string result;
    for (auto chunk = input.obtain(); chunk.size > 0; chunk = input.obtain()) {
        result.append(vespalib::stringref(chunk.data, chunk.size));
        input.evict(chunk.size);
    }
    return result;
}

} // unnamed

RankingExpressions::RankingExpressions() = default;
RankingExpressions::RankingExpressions(RankingExpressions &&rhs) noexcept = default;
RankingExpressions::~RankingExpressions() = default;

RankingExpressions &
RankingExpressions::add(const vespalib::string &name, const vespalib::string &path)
{
    _expressions.insert_or_assign(name, path);
    return *this;
}

vespalib::string
RankingExpressions::loadExpression(const vespalib::string &name) const
{
    auto pos = _expressions.find(name);
    if (pos == _expressions.end()) {
        LOG(warning, "no such ranking expression: '%s'", name.c_str());
        return {};
    }
    auto path = pos->second;
    vespalib::MappedFileInput file(path);
    if (!file.valid()) {
        LOG(warning, "rankexpression: %s -> could not read file: %s", name.c_str(), path.c_str());
        return {};
    }
    if (ends_with(path, ".lz4")) {
        size_t buffer_size = 64_Ki;
        vespalib::Lz4InputDecoder lz4_decoder(file, buffer_size);
        auto result = extract_data(lz4_decoder);
        if (lz4_decoder.failed()) {
            LOG(warning, "file contains lz4 errors (%s): %s",
                lz4_decoder.reason().c_str(), path.c_str());
            return {};
        }
        return result;
    }
    return extract_data(file);
}

}
