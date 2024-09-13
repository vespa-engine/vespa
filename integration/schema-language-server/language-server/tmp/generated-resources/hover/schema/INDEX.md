## index

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field) or [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema). Sets index parameters. Content in fields with *index* are [normalized](https://docs.vespa.ai/en/reference/schema-reference.html#normalizing) and [tokenized](https://docs.vespa.ai/en/linguistics.html#tokenization) by default. This element can be single- or multivalued:

```
index [index-name]: [property]
```

or

```
index [index-name] {
    [property]
    [property]
    …
}
```

If index name is specified it will be used instead of the field name as name of the index.  
**Deprecated:** Deprecated, use a field with the wanted name outside the document instead.
Parameters:

|                                   Property                                    |                    Occurrence                    |                                                                                                                                                                                                                  Description                                                                                                                                                                                                                  |
|-------------------------------------------------------------------------------|--------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [stemming](https://docs.vespa.ai/en/reference/schema-reference.html#stemming) | Zero to one                                      | Set the stemming of this index. Indexes without a stemming setting get their stemming setting from the fields added to the index. Setting this explicitly is useful if fields with conflicting stemming settings are added to this index.                                                                                                                                                                                                     |
| arity                                                                         | One (mandatory for predicate fields), else zero. | Set the [arity value for a predicate field](https://docs.vespa.ai/en/predicate-fields.html#index-size). The data type for the containing field must be `predicate`.                                                                                                                                                                                                                                                                           |
| lower-bound                                                                   | Zero to one                                      | Set the [lower bound value for a predicate field](https://docs.vespa.ai/en/predicate-fields.html#upper-and-lower-bounds). The data type for the containing field must be `predicate`.                                                                                                                                                                                                                                                         |
| upper-bound                                                                   | Zero to one                                      | Set the [upper bound value for predicate fields](https://docs.vespa.ai/en/predicate-fields.html#upper-and-lower-bounds). The data type for the containing field must be `predicate`.                                                                                                                                                                                                                                                          |
| dense-posting-list-threshold                                                  | Zero to one                                      | Set the [dense posting list threshold value for predicate fields](https://docs.vespa.ai/en/predicate-fields.html#dense-posting-list-threshold). The data type for the containing field must be `predicate`.                                                                                                                                                                                                                                   |
| enable-bm25                                                                   | Zero to one                                      | Enable this index field to be used with the [bm25 rank feature](https://docs.vespa.ai/en/reference/rank-features.html#bm25). This creates posting lists for the [indexes](https://docs.vespa.ai/en/proton.html#index) for this field that have interleaved features in the document id streams. This makes it fast to compute the *bm25* score.                                                                                               |
| [hnsw](https://docs.vespa.ai/en/reference/schema-reference.html#index-hnsw)   | Zero to one                                      | Specifies that an HNSW index should be built to speed up approximate nearest neighbor search. Only supported for tensor attribute fields with tensor types with: * One indexed dimension - single vector per document * One mapped and one indexed dimension - multiple vectors per document Used in combination with the [nearestNeighbor](https://docs.vespa.ai/en/reference/query-language-reference.html#nearestneighbor) query operator. |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#index)
