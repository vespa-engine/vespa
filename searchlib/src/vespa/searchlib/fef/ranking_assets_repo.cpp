// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "ranking_assets_repo.h"

using vespalib::eval::ConstantValue;

namespace search::fef {

RankingAssetsRepo::RankingAssetsRepo(const ConstantValueFactory &factory,
                                     std::shared_ptr<const RankingConstants> constants,
                                     std::shared_ptr<const RankingExpressions> expressions,
                                     std::shared_ptr<const OnnxModels> models)
    : _factory(factory),
      _constants(std::move(constants)),
      _rankingExpressions(std::move(expressions)),
      _onnxModels(std::move(models))
{
}

RankingAssetsRepo::~RankingAssetsRepo() = default;

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

const OnnxModel *
RankingAssetsRepo::getOnnxModel(const vespalib::string &name) const {
    return _onnxModels ? _onnxModels->getModel(name) : nullptr;
}

}
