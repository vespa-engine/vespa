## weightedset

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field) of type weightedset. Properties of a weighted set.

```
weightedset: [property]
```

or

```
weightedset {
    [property]
    [property]
    …
}
```

|       Property        | Occurrence  |                                                                                                                                                                                                                         Description                                                                                                                                                                                                                         |
|-----------------------|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| create-if-nonexistent | Zero to one | If the weight of a key is adjusted in a document using a partial update increment or decrement command, but the key is currently not present, the command will be ignored by default. Set this to make keys to be created in this case instead. This is useful when the weight is used to represent the count of the key. ``` field tag type weightedset<string> { indexing: attribute | summary weightedset { create-if-nonexistent remove-if-zero } } ``` |
| remove-if-zero        | Zero to one | This is the companion of `create-if-nonexistent` for the converse case: By default keys may have zero as weight. With this turned on, keys whose weight is adjusted (or set) to zero, will be removed.                                                                                                                                                                                                                                                      |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#weightedset-properties)
