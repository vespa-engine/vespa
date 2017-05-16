// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/stllike/string.h>

namespace search {
namespace features {

/**
 * Factory class for tensor rank features.
 */
class TensorFactoryBlueprint : public search::fef::Blueprint
{
protected:
    static vespalib::string ATTRIBUTE_SOURCE;
    static vespalib::string QUERY_SOURCE;

    vespalib::string _sourceType;
    vespalib::string _sourceParam;
    vespalib::string _dimension;

    bool extractSource(const vespalib::string &source);
    TensorFactoryBlueprint(const vespalib::string &baseName);
    ~TensorFactoryBlueprint();

public:
    void visitDumpFeatures(const search::fef::IIndexEnvironment &,
                           search::fef::IDumpFeatureVisitor &) const override {}
};

} // namespace features
} // namespace search
