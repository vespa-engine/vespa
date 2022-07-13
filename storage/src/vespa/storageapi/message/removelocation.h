// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/defs.h>
#include <vespa/persistence/spi/id_and_timestamp.h>
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>

namespace storage::api {

class RemoveLocationCommand : public BucketInfoCommand
{
public:
    RemoveLocationCommand(vespalib::stringref documentSelection, const document::Bucket &bucket);
    ~RemoveLocationCommand() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    const vespalib::string& getDocumentSelection() const { return _documentSelection; }
    // TODO move to factory pattern instead to disallow creating illegal combinations
    void set_only_enumerate_docs(bool only_enumerate) noexcept {
        _only_enumerate_docs = only_enumerate;
    }
    [[nodiscard]] bool only_enumerate_docs() const noexcept {
        return _only_enumerate_docs;
    }
    void set_explicit_remove_set(std::vector<spi::IdAndTimestamp> remove_set) {
        _explicit_remove_set = std::move(remove_set);
    }
    const std::vector<spi::IdAndTimestamp>& explicit_remove_set() const noexcept {
        return _explicit_remove_set;
    }
    std::vector<spi::IdAndTimestamp> steal_explicit_remove_set() const noexcept {
        return std::move(_explicit_remove_set);
    }

    DECLARE_STORAGECOMMAND(RemoveLocationCommand, onRemoveLocation);
private:
    // TODO make variant? Only one of the two may be used
    vespalib::string _documentSelection;
    std::vector<spi::IdAndTimestamp> _explicit_remove_set;
    bool _only_enumerate_docs;
};

class RemoveLocationReply : public BucketInfoReply
{
    std::vector<spi::IdAndTimestamp> _selection_matches; // For use in 1st phase GC
    uint32_t _documents_removed;
public:
    explicit RemoveLocationReply(const RemoveLocationCommand& cmd, uint32_t docs_removed = 0);
    void set_documents_removed(uint32_t docs_removed) noexcept {
        _documents_removed = docs_removed;
    }
    uint32_t documents_removed() const noexcept { return _documents_removed; }
    // TODO refactor
    void set_selection_matches(std::vector<spi::IdAndTimestamp> matches) noexcept {
        _selection_matches = std::move(matches);
    }
    const std::vector<spi::IdAndTimestamp>& selection_matches() const noexcept {
        return _selection_matches;
    }
    std::vector<spi::IdAndTimestamp> steal_selection_matches() noexcept {
        return std::move(_selection_matches);
    }
    DECLARE_STORAGEREPLY(RemoveLocationReply, onRemoveLocationReply)
};

}
