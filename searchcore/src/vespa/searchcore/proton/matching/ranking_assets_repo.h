// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_ranking_assets_repo.h"
#include "ranking_constants.h"
#include "onnx_models.h"
#include "ranking_expressions.h"
#include <vespa/eval/eval/value_cache/constant_value.h>

namespace proton::matching {

/**
 * Class that provides access to a configured set of rank constant values.
 *
 * This class maps symbolic names to assets used while setting up rank features blueprints.
 * A factory is used to instantiate constant values.
 */
class RankingAssetsRepo : public IRankingAssetsRepo {
private:
    using ConstantValueFactory = vespalib::eval::ConstantValueFactory;

    const ConstantValueFactory &_factory;
    const std::shared_ptr<const RankingConstants>   _constants;
    const std::shared_ptr<const RankingExpressions> _rankingExpressions;
    const std::shared_ptr<const OnnxModels>         _onnxModels;

public:
    RankingAssetsRepo(const ConstantValueFactory &factory,
                      std::shared_ptr<const RankingConstants> constants,
                      std::shared_ptr<const RankingExpressions> expressions,
                      std::shared_ptr<const OnnxModels> models);
    ~RankingAssetsRepo() override;
    vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &name) const override;
    vespalib::string getExpression(const vespalib::string &name) const override;
    const search::fef::OnnxModel *getOnnxModel(const vespalib::string &name) const override;
};

}
