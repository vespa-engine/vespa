// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

struct PointVector {
    float v[NUM_DIMS];
    using ConstArr = vespalib::ConstArrayRef<float>;
    operator ConstArr() const { return ConstArr(v, NUM_DIMS); }
};

static PointVector *aligned_alloc(size_t num) {
    size_t num_bytes = num * sizeof(PointVector);
    double mega_bytes = num_bytes / (1024.0*1024.0);
    fprintf(stderr, "allocate %.2f MB of vectors\n", mega_bytes);
    char *mem = (char *)malloc(num_bytes + 512);
    mem += 512;
    size_t val = (size_t)mem;
    size_t unalign = val % 512;
    mem -= unalign;
    return reinterpret_cast<PointVector *>(mem);
}

static PointVector *generatedQueries = aligned_alloc(NUM_Q);
static PointVector *generatedDocs = aligned_alloc(NUM_DOCS);

struct DocVectorAdapter : public DocVectorAccess<float>
{
    vespalib::ConstArrayRef<float> get(uint32_t docid) const override {
        ASSERT_TRUE(docid < NUM_DOCS);
        return generatedDocs[docid];
    }
};
