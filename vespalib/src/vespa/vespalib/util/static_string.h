// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string_view>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

class StaticStringView;
namespace literals {
constexpr StaticStringView operator "" _ssv(const char *literal, size_t size);
} // literals

/**
 * Contains the view of a literal string
 **/
class StaticStringView {
private:
    std::string_view _view;
    friend constexpr StaticStringView literals::operator "" _ssv(const char *, size_t);
    constexpr StaticStringView(const char *literal, size_t size) noexcept
      : _view(literal, size) {}
public:
    constexpr std::string_view view() const noexcept { return _view; }
    constexpr operator std::string_view() const noexcept { return _view; }
    vespalib::stringref ref() const noexcept { return {_view.data(), _view.size()}; }
    operator vespalib::stringref() const noexcept { return ref(); }
};

namespace literals {
constexpr StaticStringView operator "" _ssv(const char *literal, size_t size) {
    return vespalib::StaticStringView(literal, size);
}
} // literals

} // vespalib
