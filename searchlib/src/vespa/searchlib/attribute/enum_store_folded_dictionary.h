// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "enum_store_dictionary.h"
#include "i_enum_store_dictionary.h"

#include <vespa/vespalib/btree/btree.h>

namespace search {

class IEnumStore;

/**
 * Concrete dictionary for an enum store that extends the
 * functionality of a unique store dictionary.
 *
 * Special handling of value (posting list reference) is added to
 * ensure that entries with same folded key share a posting list
 * (e.g. case insensitive search) and posting list reference is found
 * for the first of these entries.
 */
class EnumStoreFoldedDictionary : public EnumStoreDictionary<EnumPostingTree> {
private:
    std::unique_ptr<EntryComparator> _folded_compare;

public:
    EnumStoreFoldedDictionary(IEnumStore& enumStore, std::unique_ptr<EntryComparator> compare,
                              std::unique_ptr<EntryComparator> folded_compare);
    ~EnumStoreFoldedDictionary() override;
    vespalib::datastore::UniqueStoreAddResult add(const EntryComparator&    comp,
                                                  std::function<EntryRef()> insertEntry) override;
    void remove(const EntryComparator& comp, EntryRef ref) override;
    void collect_folded(Index idx, EntryRef root, const std::function<void(EntryRef)>& callback) const override;
    Index remap_index(Index idx) override;
};

} // namespace search
