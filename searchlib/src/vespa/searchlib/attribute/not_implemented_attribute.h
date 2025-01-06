// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attributevector.h"

namespace search {

struct NotImplementedAttribute : AttributeVector {
    NotImplementedAttribute(std::string_view name);
    NotImplementedAttribute(std::string_view name, const Config & config);
    [[noreturn]] __attribute__((noinline)) // Preserve caller in backtrace
    void notImplemented() const;

    uint32_t getValueCount(DocId) const override;
    largeint_t getInt(DocId) const override;
    double getFloat(DocId) const override;
    std::span<const char> get_raw(DocId) const override;
    uint32_t get(DocId, largeint_t *, uint32_t) const override;
    uint32_t get(DocId, double *, uint32_t) const override;
    uint32_t get(DocId, std::string *, uint32_t) const override;
    uint32_t get(DocId, const char **, uint32_t) const override;
    uint32_t get(DocId, EnumHandle *, uint32_t) const override;
    uint32_t get(DocId, WeightedInt *, uint32_t) const override;
    uint32_t get(DocId, WeightedFloat *, uint32_t) const override;
    uint32_t get(DocId, WeightedString *, uint32_t) const override;
    uint32_t get(DocId, WeightedConstChar *, uint32_t) const override;
    uint32_t get(DocId, WeightedEnum *, uint32_t) const override;
    bool findEnum(const char *, EnumHandle &) const override;
    std::vector<EnumHandle> findFoldedEnums(const char *value) const override;

    bool is_sortable() const noexcept override;
    long onSerializeForAscendingSort(DocId, void *, long, const common::BlobConverter *) const override;
    long onSerializeForDescendingSort(DocId, void *, long, const common::BlobConverter *) const override;
    uint32_t clearDoc(DocId) override;
    uint32_t getEnum(DocId) const override;
    bool addDoc(DocId &) override;
    void onAddDocs(DocId lidLimit) override;

    std::unique_ptr<attribute::SearchContext> getSearch(QueryTermSimpleUP, const attribute::SearchContextParams &) const override;
};

}  // namespace search

