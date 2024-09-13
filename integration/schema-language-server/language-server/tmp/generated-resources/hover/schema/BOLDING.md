## bolding

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field) or [summary](https://docs.vespa.ai/en/reference/schema-reference.html#summary). Highlight matching query terms in the [summary](https://docs.vespa.ai/en/reference/schema-reference.html#summary):

```
bolding: on
```

The default is no bolding, set `bolding: on` to enable it. Note that this command is overridden by `summary: dynamic`. If both are specified, bolding will be ignored. The difference between using bolding instead of `summary: dynamic` is the latter will provide a dynamic abstract in addition to highlighting query terms, while the first only highlights. Bolding is only supported for [index](https://docs.vespa.ai/en/reference/schema-reference.html#indexing-index) fields of type string or array\<string\>.

The default XML element used to highlight the search terms is \<hi\> - to override, set *container.qr-searchers* configuration. Example using `<strong>`:


```xml
<container>
    <search>
        <config name="container.qr-searchers">
            <tag>
                <bold>
                    <open>&lt;strong&gt;</open>
                    <close>&lt;/strong&gt;</close>
                </bold>
                <separator>...</separator>
            </tag>
        </config>
    </search>
</container>

```


Maximum field byte length for bolding is 64Mb - field values larger than this will be represented as a snippet as in `summary: dynamic`.
[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#bolding)
