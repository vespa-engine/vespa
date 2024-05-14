// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/binary_hamming_distance.h>
#include <vector>
#include <cinttypes>
#include <cstdlib>
#include <cstdio>

using namespace vespalib;

int main(int argc, char* argv[]) {
    size_t vector_length = 1024/8;
    size_t num_vectors = 1;
    size_t num_reps = 100000000;

    if (argc > 2) {
        vector_length = atol(argv[2])/8;
    }
    if (argc > 3) {
        num_reps = atol(argv[3]);
    }
    if (argc > 4) {
        num_vectors = atol(argv[4]);
    }

    std::vector<uint8_t> center(vector_length);
    std::vector<uint8_t> vectors(num_vectors*vector_length);
    srand(13);
    for (uint8_t & v : center) { v = rand(); }
    for (uint8_t & v : vectors) { v = rand(); }
    uint64_t sum(0);
    for (size_t i=0; i < num_reps; i++) {
        for (size_t j(0); j < num_vectors; j++) {
            sum += binary_hamming_distance(center.data(), vectors.data() + j*vector_length, vector_length);
        }
    }

    printf("%lu vectors of %lu bits, repeated %lu times. Sum of distances = %" PRIu64 "\n", num_vectors, vector_length*8, num_reps, sum);
    return 0;
}
