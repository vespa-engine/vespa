// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/messagebus/routable.h>
#include <vespa/vespalib/component/version.h>
#include <gtest/gtest.h>
#include <array>
#include <filesystem>
#include <functional>

namespace documentapi {

struct MessageFixture : ::testing::Test {
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    const std::filesystem::path                       _data_path;
    DocumentProtocol                                  _protocol;

    // Declares what languages share serialization.
    enum {
        LANG_CPP = 0,
        LANG_JAVA,
        NUM_LANGUAGES
    };

    static constexpr std::array<uint32_t, 2> languages() noexcept {
        return {LANG_CPP, LANG_JAVA};
    }

    MessageFixture();
    ~MessageFixture() override;

    [[nodiscard]] virtual vespalib::Version tested_protocol_version() const = 0;

    using Tamper = std::function<mbus::Blob(mbus::Blob)>;
    [[nodiscard]] static mbus::Blob truncate(mbus::Blob data, size_t bytes);
    [[nodiscard]] static mbus::Blob pad(mbus::Blob data, size_t bytes);

    [[nodiscard]] const document::DocumentTypeRepo& type_repo() const noexcept { return *_repo; }

    [[nodiscard]] static bool write_file(const std::filesystem::path& filename, const mbus::Blob& blob);
    [[nodiscard]] static mbus::Blob read_file(const std::filesystem::path& filename);
    uint32_t serialize(const std::string& filename, const mbus::Routable& routable, Tamper tamper);
    uint32_t serialize(const std::string& filename, const mbus::Routable& routable) {
        return serialize(filename, routable, [](auto x) noexcept { return x; });
    }
    [[nodiscard]] mbus::Routable::UP deserialize(const std::string& filename, uint32_t classId, uint32_t lang);
    static void dump(const mbus::Blob& blob);

    [[nodiscard]] std::filesystem::path path_to_file(const std::string& filename) const {
        return _data_path / filename;
    }
    [[nodiscard]] mbus::Blob encode(const mbus::Routable& obj) const {
        return _protocol.encode(tested_protocol_version(), obj);
    }
    [[nodiscard]] mbus::Routable::UP decode(mbus::BlobRef data) const {
        return _protocol.decode(tested_protocol_version(), data);
    }
private:
    [[nodiscard]] static bool file_content_is_unchanged(const std::filesystem::path& filename, const mbus::Blob& data_to_write);
};

} // documentapi
