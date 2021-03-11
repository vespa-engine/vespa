// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simple_hash_map.h"
#include "fixed_size_hash_map.h"
#include "entry_comparator.h"

namespace vespalib::datastore {

class SimpleHashMapStripeHeld : public GenerationHeldBase
{
    std::unique_ptr<const FixedSizeHashMap> _data;
public:
    SimpleHashMapStripeHeld(size_t size, std::unique_ptr<const FixedSizeHashMap> data);
    ~SimpleHashMapStripeHeld();
};

SimpleHashMapStripeHeld::SimpleHashMapStripeHeld(size_t size, std::unique_ptr<const FixedSizeHashMap> data)
    : GenerationHeldBase(size),
      _data(std::move(data))
{
}

SimpleHashMapStripeHeld::~SimpleHashMapStripeHeld() = default;

SimpleHashMap::SimpleHashMap(std::unique_ptr<const EntryComparator> comp)
    : _gen_holder(),
      _maps(),
      _comp(std::move(comp))
{
}

SimpleHashMap::~SimpleHashMap()
{
    for (size_t i = 0; i < num_stripes; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        delete map;
    }
}

size_t
SimpleHashMap::get_stripe(const EntryComparator& comp, EntryRef key_ref) const
{
    return comp.hash(key_ref) % num_stripes;
}

void
SimpleHashMap::alloc_stripe(size_t stripe)
{
    auto map = _maps[stripe].load(std::memory_order_relaxed);
    if (map == nullptr) {
        auto umap = std::make_unique<FixedSizeHashMap>(2u, 3u, num_stripes);
        _maps[stripe].store(umap.release(), std::memory_order_release);
    } else {
        auto umap = std::make_unique<FixedSizeHashMap>(map->size() * 2 + 2, map->size() * 3 + 3, num_stripes, *map, *_comp);
        _maps[stripe].store(umap.release(), std::memory_order_release);
        hold_stripe(std::unique_ptr<const FixedSizeHashMap>(map));
    }
}

void
SimpleHashMap::hold_stripe(std::unique_ptr<const FixedSizeHashMap> map)
{
    // TODO: Provider proper held size
    auto hold = std::make_unique<SimpleHashMapStripeHeld>(0, std::move(map));
    _gen_holder.hold(std::move(hold));
}

SimpleHashMap::KvType&
SimpleHashMap::add(const EntryComparator& comp, std::function<EntryRef(void)>& insert_entry)
{
    size_t stripe = get_stripe(comp, EntryRef());
    auto map = _maps[stripe].load(std::memory_order_relaxed);
    if (map == nullptr || map->full()) {
        alloc_stripe(stripe);
        map = _maps[stripe].load(std::memory_order_relaxed);
    }
    return map->add(comp, insert_entry);
}

SimpleHashMap::KvType*
SimpleHashMap::remove(const EntryComparator& comp, EntryRef key_ref)
{
    size_t stripe = get_stripe(comp, key_ref);
    auto map = _maps[stripe].load(std::memory_order_relaxed);
    if (map == nullptr) {
        return nullptr;
    }
    return map->remove(comp, key_ref);
}

const SimpleHashMap::KvType*
SimpleHashMap::find(const EntryComparator& comp, EntryRef key_ref) const
{
    size_t stripe = get_stripe(comp, key_ref);
    auto map = _maps[stripe].load(std::memory_order_relaxed);
    if (map == nullptr) {
        return nullptr;
    }
    return map->find(comp, key_ref);
}

void
SimpleHashMap::transfer_hold_lists(generation_t generation)
{
    for (size_t i = 0; i < num_stripes; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            map->transfer_hold_lists(generation);
        }
    }
    _gen_holder.transferHoldLists(generation);
}

void
SimpleHashMap::trim_hold_lists(generation_t first_used)
{
    for (size_t i = 0; i < num_stripes; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            map->trim_hold_lists(first_used);
        }
    }
    _gen_holder.trimHoldLists(first_used);
}

size_t
SimpleHashMap::size() const noexcept
{
    size_t result = 0;
    for (size_t i = 0; i < num_stripes; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            result += map->size();
        }
    }
    return result;
}

}
