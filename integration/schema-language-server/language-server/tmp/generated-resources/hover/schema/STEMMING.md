## stemming

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field), [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema) or [index](https://docs.vespa.ai/en/reference/schema-reference.html#index). Sets how to stem a field or an index, or how to stem by default. Read more on [stemming](https://docs.vespa.ai/en/linguistics.html#stemming).

```
stemming: [stemming-type]
```

The stemming types are:

|   Type   |                                            Description                                            |
|----------|---------------------------------------------------------------------------------------------------|
| none     | No stemming: Keep words unchanged                                                                 |
| best     | Use the 'best' stem of each word according to some heuristic scoring. This is the default setting |
| shortest | Use the shortest stem of each word                                                                |
| multiple | Use multiple stems. Retains all stems returned from the linguistics library                       |

**Note:** When combining multiple fields in a [fieldset](https://docs.vespa.ai/en/reference/schema-reference.html#fieldset), all fields should use the same stemming type.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#stemming)
