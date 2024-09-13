## dictionary

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field), and specifies details of the dictionary used in the inverted index of the field. Applies only to [attributes](https://docs.vespa.ai/en/reference/schema-reference.html#attribute) annotated with `fast-search`. You can specify either `btree` or `hash`, or both.

<br />

**Note:** Note that prefix search for strings and range search for numeric fields will fall back to full scan if using `hash`. It is primarily intended for use when you have many unique terms with few occurrences (short posting lists), where the dictionary lookup cost could be significant.

<br />

Normally, `btree` is your best choice as it offers reasonable performance for both exact, prefix and range type of dictionary lookups. This is also the default. Find more details in [attribute index structures](https://docs.vespa.ai/en/attributes.html#index-structures).

Use `hash` for fields with high uniqueness (high cardinality), for example an 'id' field which is unique in the corpus where the posting list is always of size 1.

In addition, one can specify `uncased` or `cased` dictionary for string attributes, default is `uncased`. This setting is sanity checked against the field [match:casing](https://docs.vespa.ai/en/reference/schema-reference.html#match) setting.

In an `uncased` dictionary, casing is normalized by lowercasing so that 'bear' equals 'Bear' equals 'BEAR'. In a `cased` dictionary, they will all be different.

Example of a string field with a cased hash dictionary. Note that for string fields with dictionary type hash, the `dictionary` block must also include `cased`.

```
field id_str type string {
      indexing:   summary | attribute
      attribute:  fast-search
      match:      cased
      rank:       filter
      dictionary {
        hash
        cased
      }
}
  
```

<br />

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#dictionary)
