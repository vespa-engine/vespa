## id

Contained in [field](https://docs.vespa.ai/en/reference/schema-reference.html#field). Sets the numerical id of this field. All fields have a document-internal id internally for transfer and storage. Ids are usually determined programmatically as a 31-bit number. Some storage and transfer space can be saved by instead explicitly setting id's to a 7-bit number.

```
id: [positive integer]
```

An id must satisfy these requirements:

* Must be a positive integer
* Must be less than 100 or larger than 127
* Must be unique within the document and all documents this document inherits

[Read more](https://docs.vespa.ai/en/reference/schema-reference.html#id)
