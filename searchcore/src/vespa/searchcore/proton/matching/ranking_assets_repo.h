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
    RankingConstants::SP   _constants;
    RankingExpressions::SP _rankingExpressions;
    OnnxModels::SP         _onnxModels;

public:
    explicit RankingAssetsRepo(const ConstantValueFactory &factory);
    ~RankingAssetsRepo() override;
    void reconfigure(RankingConstants::SP constants, RankingExpressions::SP expressions, OnnxModels::SP models);
    vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &name) const override;
    vespalib::string getExpression(const vespalib::string &name) const override;
    const search::fef::OnnxModel *getOnnxModel(const vespalib::string &name) const override;
};

}
