// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "matchelem.h"
#include "querynode.h"
#include <memory>

using key_occ_vector = std::vector<std::unique_ptr<key_occ>>;

class key_occ : public MatchElement
{
public:
    void set_valid() override;
    void add_to_keylist(keylist& kl) override;
    void dump(std::string& s) override;
    size_t length() const override { return tokenlen; }
    const char* term() { return _term; }
    size_t word_length() const override { return 1; }
    bool complete() override { return true; }
    off_t endpos() const override { return _startpos + tokenlen; }
    off_t endtoken() const override { return _starttoken + 1; }

    int tokenlen;
    key_occ(const char* term, off_t posi, off_t tpos, int len);

private:
    const char* _term; // Pointer into first match (for debugging purposes only)

    key_occ(key_occ &);
    key_occ &operator=(key_occ &);
};


