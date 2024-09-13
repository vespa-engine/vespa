A normalized number (between 0.0 and 1.0) describing the significance of the term; used as a multiplier or weighting factor by many other text matching rank features.

This should ideally be set by a searcher in the container for global correctness as each node will estimate the significance values from the local corpus. Use the [Java API for significance](https://javadoc.io/doc/com.yahoo.vespa/container-search/latest/com/yahoo/prelude/query/TaggableItem.html#setSignificance(double)) or [YQL annotation for significance](https://docs.vespa.ai/en/reference/query-language-reference.html#significance).

As a fallback, a significance based on Robertson-Sparck-Jones term weighting is used; it is logarithmic from 1.0 for rare terms down to 0.5 for common terms (those occurring in every document seen).

Note that "rare" is defined as a frequency of 0.000001 or less. This is the term document frequency (how many documents contain the term out of all documents that can be observed), so you cannot get 1.0 as the fallback until you actually have a large number of documents (minimum 1 million) in the same search process.

See [numTerms](https://docs.vespa.ai/en/reference/rank-feature-configuration.html#term) config.

Default: 0