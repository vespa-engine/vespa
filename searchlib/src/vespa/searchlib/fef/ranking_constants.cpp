// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ranking_constants.h"

namespace search::fef {

RankingConstants::Constant::Constant(const std::string &name_in,
                                     const std::string &type_in,
                                     const std::string &filePath_in)
    : name(name_in),
      type(type_in),
      filePath(filePath_in)
{
}

RankingConstants::Constant::~Constant() = default;

bool
RankingConstants::Constant::operator==(const Constant &rhs) const
{
    return (name == rhs.name) &&
           (type == rhs.type) &&
           (filePath == rhs.filePath);
}

RankingConstants::RankingConstants()
    : _constants()
{
}

RankingConstants::~RankingConstants() = default;
RankingConstants::RankingConstants(RankingConstants &&) noexcept = default;

RankingConstants::RankingConstants(const Vector &constants)
    : _constants()
{
    for (const auto &constant : constants) {
        _constants.insert(std::make_pair(constant.name, constant));
    }
}

bool
RankingConstants::operator==(const RankingConstants &rhs) const
{
    return _constants == rhs._constants;
}

const RankingConstants::Constant *
RankingConstants::getConstant(const std::string &name) const
{
    auto itr = _constants.find(name);
    if (itr != _constants.end()) {
        return &itr->second;
    }
    return nullptr;
}

}

