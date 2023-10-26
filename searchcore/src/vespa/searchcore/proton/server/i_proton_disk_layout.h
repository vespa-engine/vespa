// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <set>

namespace proton {

class DocTypeName;

/**
 * Interface class with utility functions for handling the disk
 * directory layout for proton instance.
 */
class IProtonDiskLayout
{
public:
    virtual ~IProtonDiskLayout() = default;
    virtual void remove(const DocTypeName &docTypeName) = 0;
    virtual void initAndPruneUnused(const std::set<DocTypeName> &docTypeNames) = 0;
};

} // namespace proton
