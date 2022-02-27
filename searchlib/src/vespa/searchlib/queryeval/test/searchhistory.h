// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <ostream>
#include <string>
#include <vector>

namespace search::queryeval::test {

/**
 * Seek and unpack history for a search iterator.
 **/
struct SearchHistory {
    struct Entry {
        std::string target;
        std::string op;
        uint32_t    docid;
        Entry(const std::string &t, const std::string &o, uint32_t id)
            : target(t), op(o), docid(id) {}
        Entry(const Entry&);
        Entry(Entry&&) noexcept = default;
        ~Entry() {}
        Entry& operator=(const Entry&);
        Entry& operator=(Entry&&) noexcept = default;
        bool operator==(const Entry &rhs) const {
            return ((target == rhs.target) &&
                    (op == rhs.op) &&
                    (docid == rhs.docid));
        }
    };
    std::vector<Entry> _entries;
    SearchHistory &seek(const std::string &target, uint32_t docid) {
        _entries.push_back(Entry(target, "seek", docid));
        return *this;
    }
    SearchHistory &step(const std::string &target, uint32_t docid) {
        _entries.push_back(Entry(target, "setDocId", docid));
        return *this;
    }
    SearchHistory &unpack(const std::string &target, uint32_t docid) {
        _entries.push_back(Entry(target, "unpack", docid));
        return *this;
    }
    bool operator==(const SearchHistory &rhs) const {
        return (_entries == rhs._entries);
    }
};

std::ostream &operator << (std::ostream &out, const SearchHistory &hist) {
    out << "History:\n";
    for (size_t i = 0; i < hist._entries.size(); ++i) {
        const SearchHistory::Entry &entry = hist._entries[i];
        out << "  " << entry.target << "->" << entry.op << "(" << entry.docid << ")" << std::endl;
    }
    return out;
}

}
