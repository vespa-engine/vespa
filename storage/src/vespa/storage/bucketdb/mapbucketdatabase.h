// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketdatabase.h"
#include <map>

namespace storage {

class MapBucketDatabase : public BucketDatabase
{
public:
    MapBucketDatabase();
    ~MapBucketDatabase();

    Entry get(const document::BucketId& bucket) const override;
    void remove(const document::BucketId& bucket) override;
    void getParents(const document::BucketId& childBucket, std::vector<Entry>& entries) const override;
    void getAll(const document::BucketId& bucket, std::vector<Entry>& entries) const override;
    void update(const Entry& newEntry) override;
    void forEach(EntryProcessor&, const document::BucketId& after = document::BucketId()) const override;
    uint64_t size() const override { return _values.size() - _freeValues.size(); };
    void clear() override;

    uint32_t childCount(const document::BucketId&) const override;
    Entry upperBound(const document::BucketId& value) const override;

    void merge(MergingProcessor&) override;

    document::BucketId getAppropriateBucket(uint16_t minBits, const document::BucketId& bid) override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    std::unique_ptr<bucketdb::ReadGuard<Entry>> acquire_read_guard() const override;
    vespalib::MemoryUsage memory_usage() const noexcept override;
private:
    struct E {
        E() : value(-1), e_0(-1), e_1(-1) {};
        ~E();

        bool empty() {
            return (value == -1 && e_0 == -1 && e_1 == -1);
        };

        int value;
        int e_0;
        int e_1;
    };

    template <typename EntryType>
    void update_internal(EntryType&& new_entry);

    BucketDatabase::Entry* find(int idx, uint8_t bitCount, const document::BucketId& bid, bool create);
    bool remove(int index, uint8_t bitCount, const document::BucketId& bId);
    int findFirstInOrderNodeInclusive(int index) const;
    int upperBoundImpl(int index, uint8_t depth, const document::BucketId& value) const;

    bool forEach(int index, EntryProcessor& processor, uint8_t bitCount,
                 const document::BucketId& lowerBound, bool& process) const;

    void merge_internal(int index, MergingProcessor& processor,
                        std::vector<Entry>& to_insert,
                        std::vector<document::BucketId>& to_remove);

    void findParents(int index, uint8_t bitCount, const document::BucketId& bid, std::vector<Entry>& entries) const;
    void findAll(int index, uint8_t bitCount, const document::BucketId& bid, std::vector<Entry>& entries) const;
    uint8_t getHighestSplitBit(int index, uint8_t bitCount, const document::BucketId& bid, uint8_t minCount);
    uint32_t childCountImpl(int index, uint8_t bitCount, const document::BucketId& b) const;

    // NOT thread-safe for concurrent reads!
    class ReadGuardImpl final : public bucketdb::ReadGuard<Entry> {
        const MapBucketDatabase* _db;
    public:
        explicit ReadGuardImpl(const MapBucketDatabase& db) : _db(&db) {}
        ~ReadGuardImpl() override = default;

        void find_parents_and_self(const document::BucketId& bucket,
                                   std::vector<Entry>& entries) const override;
        uint64_t generation() const noexcept override { return 0; }
    };

    uint32_t allocate();
    uint32_t allocateValue(const document::BucketId& bid);

    std::vector<E> _db;
    std::vector<uint32_t> _free;

    std::vector<BucketDatabase::Entry> _values;
    std::vector<uint32_t> _freeValues;
};

}
