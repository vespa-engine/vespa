// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchhistory.h"

namespace search::queryeval::test {

SearchHistory::Entry::Entry(const Entry&) = default;

SearchHistory::Entry& SearchHistory::Entry::operator=(const Entry&) = default;

std::ostream &operator << (std::ostream &out, const SearchHistory &hist) {
    out << "History:\n";
    for (size_t i = 0; i < hist._entries.size(); ++i) {
        const SearchHistory::Entry &entry = hist._entries[i];
        out << "  " << entry.target << "->" << entry.op << "(" << entry.docid << ")" << std::endl;
    }
    return out;
}

}
