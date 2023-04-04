// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/aligner.h>
#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>
#include <vespa/vespalib/datastore/datastore.h>

namespace search::memoryindex {

/**
 * Class storing DocIdAndFeatures in an underlying DataStore, using 32-bit refs to access entries.
 */
class FeatureStore {
public:
    using DataStoreType = vespalib::datastore::DataStoreT<vespalib::datastore::EntryRefT<22>>;
    using RefType = DataStoreType::RefType;
    using EncodeContext = bitcompression::EG2PosOccEncodeContext<true>;
    using DecodeContextCooked = bitcompression::EG2PosOccDecodeContextCooked<true>;
    using generation_t = vespalib::GenerationHandler::generation_t;
    static constexpr uint32_t buffer_array_size = 4u; // Must be a power of 2
    using Aligner = vespalib::datastore::Aligner<buffer_array_size>;

private:
    using Schema = index::Schema;
    using DocIdAndFeatures = index::DocIdAndFeatures;
    using PosOccFieldsParams = bitcompression::PosOccFieldsParams;

    static constexpr uint32_t DECODE_SAFETY = 16;
    static constexpr uint32_t DECODE_SAFETY_ENTRIES = 16 / buffer_array_size;

    DataStoreType _store;

    // Feature Encoder
    EncodeContext _f;
    // Buffer for compressed features.
    ComprFileWriteContext _fctx;

    // Feature Decoder
    DecodeContextCooked _d;

    // Coding parameters for fields and field collections, derived
    // from schema.
    std::vector<PosOccFieldsParams> _fieldsParams;

    const Schema &_schema;

    vespalib::datastore::BufferType<uint8_t> _type;
    const uint32_t      _typeId;

    /**
     * Writes the given features to the underlying encode context.
     *
     * @param packedIndex the field or field collection owning features
     * @param features the features to be encoded
     * @return the encode offset before writing
     */
    uint64_t writeFeatures(uint32_t packedIndex, const DocIdAndFeatures &features);

    /**
     * Adds the features from the given buffer to the data store.
     *
     * @param src buffer with features
     * @param byteLen the byte length of the buffer
     * @return the entry ref for the added features
     */
    vespalib::datastore::EntryRef addFeatures(const uint8_t * src, uint64_t byteLen);

    /**
     * Adds the features currently in the underlying encode context to the data store.
     *
     * @param beginOffset the begin offset into the encode context
     * @param endOffset the end offset into the encode context
     * @return the entry ref and bit length of the features
     */
    std::pair<vespalib::datastore::EntryRef, uint64_t> addFeatures(uint64_t beginOffset, uint64_t endOffset);

    /**
     * Moves features to new location, as part of compaction.
     *
     * @param ref old reference to stored features
     * @param bitLen bit length of features to move
     * @return new reference to stored features
     */
    vespalib::datastore::EntryRef moveFeatures(vespalib::datastore::EntryRef ref, uint64_t bitLen);

public:

    /**
     * Constructor for feature store.
     *
     * @param schema The schema describing fields and field
     *               collections available, used to derive
     *               coding parameters.
     */
    FeatureStore(const Schema &schema);

    ~FeatureStore();

    /**
     * Add features to feature store
     *
     * @param packedIndex The field or field collection owning features
     * @param features    The features to be encoded
     * @return            pair with reference to stored features and
     *                    size of encoded features in bits
     */
    std::pair<vespalib::datastore::EntryRef, uint64_t> addFeatures(uint32_t packedIndex, const DocIdAndFeatures &features);

    /*
     * Decoding of bitwise compressed data can read up to DECODE_SAFETY
     * bytes beyond end of compressed data. This can cause issues with future
     * features being written after new features are made visible for readers.
     * Adding guard bytes when flushing OrderedFieldIndexInserter before
     * updating the posting lists and dictionary ensures that the decoder
     * overrun beyond the compressed data either goes into other features
     * already written or into the guard area.
     *
     * If buffer type is changed to have a nonzero num_entries_for_new_buffer then
     * extra logic to add guard bytes is needed when switching primary buffer
     * to avoid issues if the buffer is resumed as primary buffer later on.
     */
    void add_features_guard_bytes();

    /**
     * Get features from feature store.
     *
     * Method signature is not const since feature decoder is written to during calculation.
     *
     * @param packedIndex The field or field collection owning features
     * @param ref         Reference to stored features
     * @param features    The features to be decoded
     */
    void getFeatures(uint32_t packedIndex, vespalib::datastore::EntryRef ref, DocIdAndFeatures &features);


    /**
     * Setup the given decoder to be used for the given field or field collection.
     *
     * @param packedIndex The field or field collection owning features
     * @param decoder     The feature decoder
     */
    void setupForField(uint32_t packedIndex, DecodeContextCooked &decoder) const {
        decoder._fieldsParams = &_fieldsParams[packedIndex];
    }

    /**
     * Setup the given decoder to later use readFeatures() to decode the stored features.
     *
     * @param ref      Reference to stored features
     * @param decoder  The feature decoder
     */
    void setupForReadFeatures(vespalib::datastore::EntryRef ref, DecodeContextCooked &decoder) const {
        const uint8_t * bits = getBits(ref);
        decoder.setByteCompr(bits);
        constexpr uint32_t maxOffset = RefType::offsetSize() * buffer_array_size;
        decoder.setEnd(maxOffset, false);
    }

    /**
     * Setup the given decoder to later use unpackFeatures() to decode the stored features.
     *
     * @param ref      Reference to stored features
     * @param decoder  The feature decoder
     */
    void setupForUnpackFeatures(vespalib::datastore::EntryRef ref, DecodeContextCooked &decoder) const {
        decoder.setByteCompr(getBits(ref));
    }

    /**
     * Calculate size of encoded features.
     *
     * Method signature is not const since feature decoder is written to during calculation.
     *
     * @param packedIndex The field or field collection owning features
     * @param ref         Reference to stored features
     * @return            size of features in bits
     */
    size_t bitSize(uint32_t packedIndex, vespalib::datastore::EntryRef ref);

    /**
     * Get byte address of stored features
     *
     * @param ref Reference to stored features
     * @return    byte address of stored features
     */
    const uint8_t *getBits(vespalib::datastore::EntryRef ref) const {
        RefType iRef(ref);
        return _store.getEntryArray<uint8_t>(iRef, buffer_array_size);
    }

    /**
     * Move features to new location, as part of compaction.
     *
     * @param packedIndex The field or field collection owning features
     * @param ref         Old reference to stored features
     * @return            New reference to stored features
     */
    vespalib::datastore::EntryRef moveFeatures(uint32_t packedIndex, vespalib::datastore::EntryRef ref);

    const std::vector<PosOccFieldsParams> &getFieldsParams() const { return _fieldsParams; }

    void reclaim_memory(generation_t oldest_used_gen) { _store.reclaim_memory(oldest_used_gen); }
    void assign_generation(generation_t current_gen) { _store.assign_generation(current_gen); }
    void reclaim_all_memory() { _store.reclaim_all_memory();}
    std::unique_ptr<vespalib::datastore::CompactingBuffers> start_compact();
    vespalib::MemoryUsage getMemoryUsage() const { return _store.getMemoryUsage(); }
    vespalib::datastore::MemoryStats getMemStats() const { return _store.getMemStats(); }
};

}
