## mutate

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). Specifies mutating operations you can do to each of the documents that make it through the 4 query phases, *on-match* , *on-first-phase* , *on-second-phase* and *on-summary*.

```
mutate {
    [phase name] { [attribute name] [operation] [numeric_value] }
}
```

The phases are:

|      Name       |                                                                                                                  Description                                                                                                                  |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| on-match        | All documents that satisfies the query.                                                                                                                                                                                                       |
| on-first-phase  | All documents from [on-match](https://docs.vespa.ai/en/reference/schema-reference.html#on-match), and is not dropped due the optional [rank-score-drop-limit](https://docs.vespa.ai/en/reference/schema-reference.html#rank-score-drop-limit) |
| on-second-phase | All documents from [on-first-phase](https://docs.vespa.ai/en/reference/schema-reference.html#on-first-phase) that makes it onto the [second-phase](https://docs.vespa.ai/en/reference/schema-reference.html#secondphase-rank) heap.           |
| on-summary      | All documents where are a summary is requested.                                                                                                                                                                                               |

The attribute must be a single value numeric attribute, enabled as [mutable](https://docs.vespa.ai/en/reference/schema-reference.html#mutable). It must also be defined outside of the [document](https://docs.vespa.ai/en/reference/schema-reference.html#document) clause.

| Operation |                    Description                     |
|-----------|----------------------------------------------------|
| =         | Set the value of the attribute to the given value. |
| +=        | Add the given value to the attribute               |
| -=        | Subtract the given value from the attribute        |

Find examples and use cases in [rank phase statistics](https://docs.vespa.ai/en/phased-ranking.html#rank-phase-statistics).
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#mutate)
