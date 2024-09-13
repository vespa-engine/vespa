## query-command

Contained in [fieldset](https://docs.vespa.ai/en/reference/schema-reference.html#fieldset), [field](https://docs.vespa.ai/en/reference/schema-reference.html#field) or [struct-field](https://docs.vespa.ai/en/reference/schema-reference.html#struct-field). Specifies a function to be performed on query terms to the indexes of this field when searching. The Search Container server has support for writing Vespa Searcher plugins which processes these commands.

```
query-command: [an identifier or quoted string]
```

If you write a plugin searcher which needs some index-specific configuration parameter, that parameter can be set here.

There is one built-in query-command available: `phrase-segmenting`. If this is set, terms connected by non-word characters in user queries (such as "a.b") will be parsed to a phrase item, instead of by default, an AND item where these terms have connectivity set to 1.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#query-command)
