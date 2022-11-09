<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# List of possible future enhancements and features

This lists some possible improvements to Vespa which have been considered or requested, can be developed relatively 
independently of other work, and are not yet under development. For more information on the code structure in Vespa, see
[Code-map.md](Code-map.md).


## Support query profiles for document processors

**Effort:** Low<br/>
**Difficulty:** Low<br/>
**Skills:** Java

Query profiles make it simple to support multiple buckets, behavior profiles for different use cases etc by providing 
bundles of parameters accessible to Searchers processing queries. Writes go through a similar chain of processors - 
Document Processors, but have no equivalent support for parametrization. This is to allow configuration of document 
processor profiles by reusing the query profile support also for document processors.

See [slack discussion](https://vespatalk.slack.com/archives/C01QNBPPNT1/p1624176344102300) for more details.

**Code pointers:**
- [Query profiles](https://github.com/vespa-engine/vespa/blob/master/container-search/src/main/java/com/yahoo/search/query/profile/QueryProfile.java)
- [Document processors](https://github.com/vespa-engine/vespa/blob/master/docproc/src/main/java/com/yahoo/docproc/DocumentProcessor.java)


## Java implementation of the content layer for testing

**Effort:** Medium<br/>
**Difficulty:** Low<br/>
**Skills:** Java

There is currently support for creating Application instances programmatically in Java to unit test application package
functionality (see com.yahoo.application.Application). However, only Java component functionality can be tested in this 
way as the content layer is not available, being implemented in C++. A Java implementation, of some or all of the 
functionality would enable developers to do more testing locally within their IDE. This is medium effort because 
performance is not a concern and some components, such as ranking expressions and features are already available as 
libraries (see the searchlib module).

**Code pointers:**
- Content cluster mock in Java  (currently empy): [ContentCluster](https://github.com/vespa-engine/vespa/blob/master/application/src/main/java/com/yahoo/application/content/ContentCluster.java)
- The model of a search definition this must consume config from: [Search](https://github.com/vespa-engine/vespa/blob/master/config-model/src/main/java/com/yahoo/searchdefinition/Search.java)


## Indexed search in maps

**Effort:** Medium<br/>
**Difficulty:** Medium<br/>
**Skills:** C++, multithreading, performance, indexing, data structures

Vespa supports maps and and making them searchable in memory by declaring as an attribute. 
However, maps cannot be indexed as text-search disk indexes. 

**Code pointers:**
- [Current text indexes](https://github.com/vespa-engine/vespa/tree/master/searchlib/src/vespa/searchlib/index)


## Global writes

**Effort:** High<br/>
**Difficulty:** High<br/>
**Skills:** C++, Java, distributed systems, performance, multithreading, network, distributed consistency

Vespa instances distribute data automatically within clusters, but these clusters are meant to consist of co-located 
machines - the distribution algorithm is not suitable for global distribution across datacenters because it cannot 
seamlessly tolerate datacenter-wide outages and does not attempt to minimize bandwidth usage between datacenters.
Application usually achieve global precense instead by setting up multiple independent instances in different 
datacenters and write to all in parallel. This is robust and works well on average, but puts additional burden on 
applications to achieve cross-datacenter data consistency on datacenter failures, and does not enable automatic 
data recovery across datacenters, such that data redundancy is effectively required within each datacenter. 
This is fine in most cases, but not in the case where storage space drives cost and intermittent loss of data coverage 
(completeness as seen from queries) is tolerable.

A solution should sustain current write rates (tens of thousands of writes per ndoe per second), sustain write and read 
rates on loss of connectivity to one (any) data center, re-establish global data consistency when a lost datacenter is 
recovered and support some degree of tradeoff between consistency and operation latency (although the exact modes to be 
supported is part of the design and analysis needed).

**Code pointers:**
- [Document API](https://github.com/vespa-engine/vespa/tree/master/documentapi/src/main/java/com/yahoo/documentapi)


## Global dynamic tensors

**Effort:** High<br/>
**Difficulty:** High<br/>
**Skills:** Java, C++, distributed systems, performance, networking, distributed consistency

Tensors in ranking models may either be passed with the query, be part of the document or be configured as part of the 
application package (global tensors). This is fine for many kinds of models but does not support the case of really 
large tensors (which barely fit in memory) and/or dynamically changing tensors (online learning of global models). 
These use cases require support for global tensors (tensors available locally on all content nodes during execution 
but not sent with the query or residing in documents) which are not configured as part of the application package but 
which are written independently and dynamically updateable at a high write rate. To support this at large scale, with a
high write rate, we need a small cluster of nodes storing the source of truth of the global tensor and which have 
perfect consistency. This in turn must push updates to all content nodes in a best effort fashion given a fixed bandwidth
budget, such that query execution and document write traffic is prioritized over ensuring perfect consistency of global
model updates.

**Code pointers:**
- Tensor modify operation (for document tensors): [Java](https://github.com/vespa-engine/vespa/blob/master/document/src/main/java/com/yahoo/document/update/TensorModifyUpdate.java), [C++](https://github.com/vespa-engine/vespa/blob/master/document/src/vespa/document/update/tensor_modify_update.h)


## Feed clients in different languages

**Effort:** Low<br/>
**Difficulty:** Low<br/>
**Skills:** Knowledge of a decent HTTP/2 library in some language

/document/v1 is a RESTified HTTP API which exposes the Vespa Document API to the
outside of the application's Java containers. The design of this API is simple,
with each operation modelled as a single HTTP request, and its result as
a single HTTP response. While it was previously not possible to achieve comparable
throughput using this API to what the undocumented, custom-protocol /feedapi offered,
this changed with HTTP/2 support in Vespa. The clean design of /document/v1 makes it
easy to interface with from any language and runtime that support HTTP/2.
An implementation currently only exists for Java, and requires a JDK8+ runtime,
and implementations in other languages are very welcome. The below psuedo-code could
be a starting point for an asynchronous implementation with futures and promises.

Let `http` be an asynchronous HTTP/2 client, which returns a `future` for each request.
A `future` will complete some time in the future, at which point dependent computations
will trigger, depending on the result of the operation. A `future` is obtained from a
`promise`, and completes when the `promise` is completed. An efficient feed client is then:

```
inflight = map<document_id, promise>()

func dispatch(operation: request, result: promise, attempt: int): void
    http.send(operation).when_complete(response => handle(operation, response, result, attempt))

func handle(operation: request, response: response, result: promise, attempt: int): void
    if retry(response, attempt):
        dispatch(operation, result, attempt + 1)
    else:
        result.complete(response)

func enqueue(operation): future
    result_promise = promise()
    result = result_promise.get_future()
    previous = inflight.put(document.id, result)  # store `result` under `id` and obtain previous mapping
    if previous == NIL:
        while inflight.size >= max_inflight(): wait()
        dispatch(operation, result, 1)
    else:
        previous.when_complete(ignored => dispatch(operation, result, 1))
    result.when_complete(ignored => inflight.remove_value(result)) # remove mapping unless it has been replaced
    return result
```

Apply synchronization as necessary. The `inflight` map is used to serialise multiple operations
to the same document id: the mapped entry for each id is the tail of a linked queue where new
dependents may be added, while the queue is emptied from the head one entry at a time, whenever
a dependency (`previous`) completes computation. `enqueue` blocks until there is room in the client.

**Code pointers:**
- [Java feed client](https://github.com/vespa-engine/vespa/blob/master/vespa-feed-client-api/src/main/java/ai/vespa/feed/client/FeedClient.java)
