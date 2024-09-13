## fieldset

Contained in [schema](https://docs.vespa.ai/en/reference/schema-reference.html#schema). See [example use](https://docs.vespa.ai/en/schemas.html#fieldset).  
**Note:** this is not related to the [Document fieldset](https://docs.vespa.ai/en/documents.html#fieldsets). Also see the [FAQ](https://docs.vespa.ai/en/faq.html#must-all-fields-in-a-fieldset-have-compatible-type-and-matching-settings) for a discussion of what happens when using different types / match settings.

A fieldset groups fields together for searching:

```
fieldset myfieldset {
    fields: a,b,c
}
```

Create a fieldset named `default` to be used as the default (i.e. when not specified in the query):

```
fieldset default {
    fields: a,b,c
}
```

See [example queries using fieldset](https://docs.vespa.ai/en/query-api.html#using-a-fieldset).

The fields in the fieldset should be as similar as possible in terms of indexing clause and [match mode](https://docs.vespa.ai/en/reference/schema-reference.html#match). If they are not, test the application thoroughly. Having different match modes for the fields in the fieldset generates a warning during application deployment. If specific match settings for the fieldset is needed, such as *exact* , specify it using *match*:

```
fieldset myfieldset {
    fields: a,b,c
    match {
        exact
    }
}
```

Use [query-commands](https://docs.vespa.ai/en/reference/schema-reference.html#query-command) in the field set to set search settings. Example:

```
fieldset myfieldset {
    fields: a,b,c
    query-command:"exact @@"
}
```

Adding a fieldset will not create extra index structures in memory / on disk, it is just a mapping.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#fieldset)
