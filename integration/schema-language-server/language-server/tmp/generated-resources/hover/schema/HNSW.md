## hnsw

Contained in [index](https://docs.vespa.ai/en/reference/schema-reference.html#index). Specifies that an HNSW index should be built to speed up approximate nearest neighbor search using the [nearestNeighbor](https://docs.vespa.ai/en/reference/query-language-reference.html#nearestneighbor) query operator. This implements a modified version of the Hierarchical Navigable Small World (HNSW) graphs algorithm ([paper](https://arxiv.org/abs/1603.09320)).

Only supported for the following tensor attribute field types:

* Single vector per document: Tensor type with one indexed dimension. Example: `tensor<float>(x[3])`
* Multiple vectors per document: Tensor type with one mapped and one indexed dimension. Example: `tensor<float>(m{},x[3])`

HNSW indexes are not supported in [streaming search](https://docs.vespa.ai/en/streaming-search.html#differences-in-streaming-search) .

<br />

```
hnsw {
    [parameter]: [value]
    [parameter]: [value]
    ...
}
```

The following parameters are used when building the index graph:

|           Parameter            |                                                                                                                  Description                                                                                                                  |
|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| max-links-per-node             | Specifies how many links per HNSW node to select when building the graph. Default value is 16. In [HNSWlib](https://github.com/nmslib/hnswlib/blob/master/ALGO_PARAMS.md) (implementation based on the paper) this parameter is known as *M*. |
| neighbors-to-explore-at-insert | Specifies how many neighbors to explore when inserting a document in the HNSW graph. Default value is 200. In HNSWlib this parameter is known as *ef_construction*.                                                                           |

The [distance metric](https://docs.vespa.ai/en/reference/schema-reference.html#distance-metric) specified on the attribute is used when building and searching the graph. Example:

```
index {
    hnsw {
        max-links-per-node: 16
        neighbors-to-explore-at-insert: 200
    }
}
```

See [Approximate Nearest Neighbor Search using HNSW Index](https://docs.vespa.ai/en/approximate-nn-hnsw.html) for examples of use, and see [Approximate Nearest Neighbor Search in Vespa - Part 1](https://blog.vespa.ai/approximate-nearest-neighbor-search-in-vespa-part-1/) blog post for how the Vespa team selected HNSW as the baseline algorithm for extension and integration in Vespa.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#index-hnsw)
