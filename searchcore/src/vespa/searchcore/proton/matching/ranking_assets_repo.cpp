// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ranking_assets_repo.h"

using vespalib::eval::ConstantValue;

namespace proton::matching {

RankingAssetsRepo::RankingAssetsRepo(const ConstantValueFactory &factory)
    : _factory(factory),
      _constants()
{
}

RankingAssetsRepo::~RankingAssetsRepo() = default;

void
RankingAssetsRepo::reconfigure(RankingConstants::SP constants, RankingExpressions::SP expressions, OnnxModels::SP models)
{
    _constants = std::move(constants);
    _rankingExpressions = std::move(expressions);
    _onnxModels = std::move(models);
}

ConstantValue::UP
RankingAssetsRepo::getConstant(const vespalib::string &name) const
{
    if ( ! _constants) return {};
    const RankingConstants::Constant *constant = _constants->getConstant(name);
    if (constant != nullptr) {
        return _factory.create(constant->filePath, constant->type);
    }
    return {};
}

vespalib::string
RankingAssetsRepo::getExpression(const vespalib::string &name) const {
    return _rankingExpressions ? _rankingExpressions->loadExpression(name) : "";
}

const search::fef::OnnxModel *
RankingAssetsRepo::getOnnxModel(const vespalib::string &name) const {
    return _onnxModels ? _onnxModels->getModel(name) : nullptr;
}

}
