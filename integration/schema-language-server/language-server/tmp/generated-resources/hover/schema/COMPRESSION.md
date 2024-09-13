## compression

**Deprecated:** see [deprecations](https://docs.vespa.ai/en/vespa8-release-notes.html#compression).

Contained in [document](https://docs.vespa.ai/en/reference/schema-reference.html#document). If a compression level is set within this element, **lz4** compression is enabled for whole documents.

```
compression {
    [body]
}
```

The body of a compression specification is optional and may contain:

|   Name    | Occurrence  |                                                                                                                Description                                                                                                                |
|-----------|-------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| type      | Zero to one | **LZ4** is the only valid compression method.                                                                                                                                                                                             |
| level     | Zero to one | Enable compression. LZ4 is linear and 9 means HC(high compression)                                                                                                                                                                        |
| threshold | Zero to one | A percentage (multiplied by 100) giving the maximum size that compressed data can have to keep the compressed value. If the resulting compressed data is higher than this, the document will be stored uncompressed. Default value is 95. |

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#compression)
