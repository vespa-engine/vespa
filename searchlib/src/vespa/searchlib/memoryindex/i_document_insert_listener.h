// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <vespa/searchlib/datastore/entryref.h>

namespace search {
namespace memoryindex {

/**
 * Interface used to track which {wordRef, fieldId} pairs that are
 * inserted into the memory index dictionary for a document.
 */
class IDocumentInsertListener
{
public:
    virtual ~IDocumentInsertListener() {}
    virtual void insert(datastore::EntryRef wordRef, uint32_t docId) = 0;
    virtual void flush() = 0;
};


} // namespace memoryindex
} // namespace search

