## match-phase

Contained in [rank-profile](https://docs.vespa.ai/en/reference/schema-reference.html#rank-profile). The match-phase feature lets you increase performance by limiting hits exposed to first-phase ranking to the highest (lowest) values of some attribute. The performance gain may be substantial, especially with an expensive first-phase function. The quality loss is dependent on how well the chosen attribute correlates with the first-phase score.

Documents which have no value of the chosen attribute will be taken as having the value 0.

See also [graceful degradation](https://docs.vespa.ai/en/graceful-degradation.html#match-phase-degradation) and [result diversity](https://docs.vespa.ai/en/result-diversity.html#match-phase-diversity).

```
match-phase {
    attribute: [numeric single value attribute]
    order: [ascending | descending]
    max-hits: [integer]
}
```

|   Name    |                                                                                                                                                                                                                              Description                                                                                                                                                                                                                               |
|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| attribute | The quality attribute that decides which documents are a match if the match phase estimates that there will be more than [max-hits](https://docs.vespa.ai/en/reference/schema-reference.html#match-phase-max-hits) hits. The attribute must be single-value numeric with [fast-search](https://docs.vespa.ai/en/reference/schema-reference.html#attribute) enabled. It should correlate with the order which would be produced by a full query evaluation. No default. |
| order     | Whether the attribute should be used in `descending` order (prefer documents with a high value) or `ascending` order (prefer documents with a low value). Usually it is not necessary to specify this, as the default value `descending` is by far the most common.                                                                                                                                                                                                    |
| max-hits  | The max hits each content node should attempt to produce in the match phase. Usually a number like 10000 works well here.                                                                                                                                                                                                                                                                                                                                              |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#match-phase)
