## Fieldset

A fieldset in Vespa is a grouping of fields that are queried together or returned for a GET or VISIT operation. It allows for searching across multiple fields without adding indexing/storage overhead. The fields in a fieldset should share the same match and linguistic processing settings. Fieldsets can be specified in the schema configuration and used to optimize search operations.

[Read more](https://docs.vespa.ai/en/schemas.html#fieldset)