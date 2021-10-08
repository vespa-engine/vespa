// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace searchcorespi {

namespace index {

struct IDiskIndex;
struct IMemoryIndex;

}

/*
 * Interface for visiting an index searchable containing disk and
 * memory indexes.
 */
class IndexSearchableVisitor
{
public:
    virtual ~IndexSearchableVisitor() { }
    virtual void visit(const index::IDiskIndex &index) = 0;
    virtual void visit(const index::IMemoryIndex &index) = 0;
};

}  // namespace searchcorespi
