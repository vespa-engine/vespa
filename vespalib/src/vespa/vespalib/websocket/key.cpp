// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "key.h"
#include <vespa/vespalib/util/sha1.h>

namespace vespalib::ws {

namespace {

const char *base64_chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                           "abcdefghijklmnopqrstuvwxyz"
                           "0123456789+/";

char id(int value) { return base64_chars[value & 0x3f]; }
vespalib::string encode64(const char *data, size_t len) {
    uint8_t tmp[3];
    vespalib::string result;
    for (size_t i = 0; i < len; i += 3) {
        tmp[0] = data[i];
        tmp[1] = (i + 1 < len) ? data[i + 1] : 0;
        tmp[2] = (i + 2 < len) ? data[i + 2] : 0;
        result.append(id(tmp[0] >> 2));
        result.append(id((tmp[0] << 4) | (tmp[1] >> 4)));
        result.append((i + 1 < len) ? id((tmp[1] << 2) | (tmp[2] >> 6)) : '=');
        result.append((i + 2 < len) ? id(tmp[2]) : '=');
    }
    return result;
}

} // namespace vespalib::ws::<unnamed>

vespalib::string
Key::create()
{
    return "dGhlIHNhbXBsZSBub25jZQ==";
}

vespalib::string
Key::accept(const vespalib::string &key)
{
    char hash[20];
    vespalib::string hash_input(key);
    hash_input.append("258EAFA5-E914-47DA-95CA-C5AB0DC85B11");
    Sha1::hash(hash_input.data(), hash_input.size(), hash, 20);
    return encode64(hash, 20);
}

} // namespace vespalib::ws
