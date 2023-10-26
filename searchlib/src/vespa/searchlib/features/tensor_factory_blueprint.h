// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/eval/eval/value_type.h>

namespace search::features {

/**
 * Factory class for tensor rank features.
 */
class TensorFactoryBlueprint : public fef::Blueprint
{
protected:
    static vespalib::string ATTRIBUTE_SOURCE;
    static vespalib::string QUERY_SOURCE;

    vespalib::string _sourceType;
    vespalib::string _sourceParam;
    vespalib::string _dimension;
    vespalib::eval::ValueType _valueType;

    bool extractSource(const vespalib::string &source);
    TensorFactoryBlueprint(const vespalib::string &baseName);
    ~TensorFactoryBlueprint();

public:
    void visitDumpFeatures(const fef::IIndexEnvironment &,
                           fef::IDumpFeatureVisitor &) const override {}
};

}
