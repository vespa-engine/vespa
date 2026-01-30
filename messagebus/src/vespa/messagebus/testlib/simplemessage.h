// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/messagebus/message.h>
#include <optional>

namespace mbus {

class SimpleMessage : public Message {
private:
    string                     _value;
    bool                       _hasSeqId;
    uint64_t                   _seqId;
    std::optional<std::string> _foo_meta;
    std::optional<std::string> _bar_meta;

public:
    SimpleMessage(const string &str);
    SimpleMessage(const string &str, bool hasSeqId, uint64_t seqId);
    ~SimpleMessage();

    void setValue(const string &value);
    const string &getValue() const;
    int getHash() const;
    const string & getProtocol() const override;
    uint32_t getType() const override;
    bool hasSequenceId() const override;
    uint64_t getSequenceId() const override;
    uint32_t getApproxSize() const override;
    uint8_t priority() const override { return 8; }
    string toString() const override { return _value; }

    void set_foo_meta(std::optional<std::string> s) noexcept { _foo_meta = std::move(s); }
    void set_bar_meta(std::optional<std::string> s) noexcept { _bar_meta = std::move(s); }

    const std::optional<std::string>& foo_meta() const noexcept { return _foo_meta; }
    const std::optional<std::string>& bar_meta() const noexcept { return _bar_meta; }

    bool hasMetadata() const noexcept override;
    void injectMetadata(MetadataInjector&) const override;
    void extractMetadata(const MetadataExtractor&) override;
};

} // namespace mbus

