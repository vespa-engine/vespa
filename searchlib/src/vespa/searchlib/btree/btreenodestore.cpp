// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "btreenodestore.hpp"
#include "btreenode.h"
#include "btreerootbase.h"
#include "btreeroot.h"
#include "btreenodeallocator.h"
#include <vespa/searchlib/datastore/datastore.h>

namespace search
{

namespace btree
{

template class BTreeNodeStore<uint32_t, uint32_t,
                              NoAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;
template class BTreeNodeStore<uint32_t, BTreeNoLeafData,
                              NoAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;
template class BTreeNodeStore<uint32_t, int32_t,
                              MinMaxAggregated,
                              BTreeDefaultTraits::INTERNAL_SLOTS,
                              BTreeDefaultTraits::LEAF_SLOTS>;

typedef EntryRefT<22> MyRef;

typedef BTreeNodeStore<uint32_t, uint32_t, NoAggregated,
                       BTreeDefaultTraits::INTERNAL_SLOTS,
                       BTreeDefaultTraits::LEAF_SLOTS>        MyNodeStore1;
typedef BTreeNodeStore<uint32_t, BTreeNoLeafData, NoAggregated,
                       BTreeDefaultTraits::INTERNAL_SLOTS,
                       BTreeDefaultTraits::LEAF_SLOTS> MyNodeStore2;
typedef BTreeNodeStore<uint32_t, int32_t, MinMaxAggregated,
                       BTreeDefaultTraits::INTERNAL_SLOTS,
                       BTreeDefaultTraits::LEAF_SLOTS>        MyNodeStore3;

typedef BTreeLeafNode<uint32_t, uint32_t, NoAggregated>         MyEntry1;
typedef BTreeLeafNode<uint32_t, BTreeNoLeafData, NoAggregated>  MyEntry2;
typedef BTreeInternalNode<uint32_t, NoAggregated>               MyEntry4;
typedef BTreeLeafNode<uint32_t, int32_t, MinMaxAggregated>     MyEntry5;
typedef BTreeInternalNode<uint32_t, MinMaxAggregated>           MyEntry6;

template
std::pair<MyRef, MyEntry1 *>
DataStoreT<MyRef>::allocNewEntryCopy<MyEntry1>(uint32_t, const MyEntry1 &);

template
std::pair<MyRef, MyEntry2 *>
DataStoreT<MyRef>::allocNewEntryCopy<MyEntry2>(uint32_t, const MyEntry2 &);

template
std::pair<MyRef, MyEntry4 *>
DataStoreT<MyRef>::allocNewEntryCopy<MyEntry4>(uint32_t, const MyEntry4 &);

template
std::pair<MyRef, MyEntry5 *>
DataStoreT<MyRef>::allocNewEntryCopy<MyEntry5>(uint32_t, const MyEntry5 &);

template
std::pair<MyRef, MyEntry6 *>
DataStoreT<MyRef>::allocNewEntryCopy<MyEntry6>(uint32_t, const MyEntry6 &);

template
std::pair<MyRef, MyEntry1 *>
DataStoreT<MyRef>::allocEntry<MyEntry1, BTreeNodeReclaimer>(uint32_t);

template
std::pair<MyRef, MyEntry2 *>
DataStoreT<MyRef>::allocEntry<MyEntry2, BTreeNodeReclaimer>(uint32_t);

template
std::pair<MyRef, MyEntry4 *>
DataStoreT<MyRef>::allocEntry<MyEntry4, BTreeNodeReclaimer>(uint32_t);

template
std::pair<MyRef, MyEntry5 *>
DataStoreT<MyRef>::allocEntry<MyEntry5, BTreeNodeReclaimer>(uint32_t);

template
std::pair<MyRef, MyEntry6 *>
DataStoreT<MyRef>::allocEntry<MyEntry6, BTreeNodeReclaimer>(uint32_t);

template
std::pair<MyRef, MyEntry1 *>
DataStoreT<MyRef>::allocEntryCopy<MyEntry1, BTreeNodeReclaimer>(
        uint32_t, const MyEntry1 &);

template
std::pair<MyRef, MyEntry2 *>
DataStoreT<MyRef>::allocEntryCopy<MyEntry2, BTreeNodeReclaimer>(
        uint32_t, const MyEntry2 &);

template
std::pair<MyRef, MyEntry4 *>
DataStoreT<MyRef>::allocEntryCopy<MyEntry4, BTreeNodeReclaimer>(
        uint32_t, const MyEntry4 &);

template
std::pair<MyRef, MyEntry5 *>
DataStoreT<MyRef>::allocEntryCopy<MyEntry5, BTreeNodeReclaimer>(
        uint32_t, const MyEntry5 &);


template
std::pair<MyRef, MyEntry6 *>
DataStoreT<MyRef>::allocEntryCopy<MyEntry6, BTreeNodeReclaimer>(
        uint32_t, const MyEntry6 &);



} // namespace btree

} // namespace search
