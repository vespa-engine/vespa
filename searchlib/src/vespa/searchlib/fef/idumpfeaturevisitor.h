// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace search::fef {

/**
 * This interface is implemented by objects that want to visit all
 * dump features.
 **/
class IDumpFeatureVisitor
{
public:
    /**
     * Visit a feature that should be dumped when doing a full feature
     * dump. Note that full feature names must be used, for example
     * 'foo(a,b).out'.
     *
     * @param name full feature name
     **/
    virtual void visitDumpFeature(const std::string &name) = 0;
    virtual ~IDumpFeatureVisitor() = default;
};

}

