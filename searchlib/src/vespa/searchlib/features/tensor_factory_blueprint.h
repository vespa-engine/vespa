// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/eval/eval/value_type.h>
#include <string>

namespace search::features {

/**
 * Factory class for tensor rank features.
 */
class TensorFactoryBlueprint : public fef::Blueprint
{
protected:
    static std::string ATTRIBUTE_SOURCE;
    static std::string QUERY_SOURCE;

    std::string _sourceType;
    std::string _sourceParam;
    std::string _dimension;
    vespalib::eval::ValueType _valueType;

    bool extractSource(const std::string &source);
    TensorFactoryBlueprint(const std::string &baseName);
    ~TensorFactoryBlueprint();

public:
    void visitDumpFeatures(const fef::IIndexEnvironment &,
                           fef::IDumpFeatureVisitor &) const override {}
};

}
