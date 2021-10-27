// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

TopK find_with_nns(uint32_t sk, NNS_API &nns, uint32_t qid) {
    TopK result;
    const PointVector &qv = generatedQueries[qid];
    vespalib::ConstArrayRef<float> query(qv.v, NUM_DIMS);
    auto rv = nns.topK(result.K, query, sk);
    for (size_t i = 0; i < result.K; ++i) {
        result.hits[i] = Hit(rv[i].docid, rv[i].sq.distance);
    }
    return result;
}
