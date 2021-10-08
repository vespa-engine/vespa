// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/index/docidandfeatures.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vespa/searchlib/bitcompression/posocccompression.h>
#include <vespa/searchlib/bitcompression/posocc_fields_params.h>
#include <vespa/vespalib/datastore/datastore.h>

namespace search::memoryindex {

/**
 * Class storing DocIdAndFeatures in an underlying DataStore, using 32-bit refs to access entries.
 */
class FeatureStore {
public:
    using DataStoreType = vespalib::datastore::DataStoreT<vespalib::datastore::AlignedEntryRefT<22, 2>>;
    using RefType = DataStoreType::RefType;
    using EncodeContext = bitcompression::EG2PosOccEncodeContext<true>;
    using DecodeContextCooked = bitcompression::EG2PosOccDecodeContextCooked<true>;
    using generation_t = vespalib::GenerationHandler::generation_t;

private:
    using Schema = index::Schema;
    using DocIdAndFeatures = index::DocIdAndFeatures;
    using PosOccFieldsParams = bitcompression::PosOccFieldsParams;

    static const uint32_t DECODE_SAFETY = 16;

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
        uint32_t bufferId = RefType(ref).bufferId();
        const vespalib::datastore::BufferState &state = _store.getBufferState(bufferId);
        decoder.setEnd(
                ((_store.getEntry<uint8_t>(RefType(state.size(), bufferId)) -
                  bits) + 7) / 8,
                false);
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
        return _store.getEntry<uint8_t>(iRef);
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

    void trimHoldLists(generation_t usedGen) { _store.trimHoldLists(usedGen); }
    void transferHoldLists(generation_t generation) { _store.transferHoldLists(generation); }
    void clearHoldLists() { _store.clearHoldLists();}
    std::vector<uint32_t> startCompact() { return _store.startCompact(_typeId); }
    void finishCompact(const std::vector<uint32_t> & toHold) { _store.finishCompact(toHold); }
    vespalib::MemoryUsage getMemoryUsage() const { return _store.getMemoryUsage(); }
    vespalib::datastore::DataStoreBase::MemStats getMemStats() const { return _store.getMemStats(); }
};

}
