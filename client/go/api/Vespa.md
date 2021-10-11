# Vespa development environment

Vespa is an engine for low-latency computation over large data sets. 
It stores and indexes your data so that queries, selection and processing over 
the data can be performed at serving time. Functionality can be customized and 
extended with application components hosted within Vespa.

Vespa allows application developers to create backend and middleware systems 
which scale to accommodate large amounts of data and high loads without 
sacrificing latency or reliability. A Vespa instance consists of a number of 
stateless Java container clusters and zero or more content clusters storing data.

Vespa accepts the following operations:

- Writes: Put (add and replace) and remove documents, and update fields in these.
- Lookup of a document (or some subset of it) by id.
- Queries: Select documents whose fields match a boolean combination of conditions; 
matches are either sorted, ranked or grouped. Ranking is done according to a 
ranking expression, which can be simple mathematical function, express complex 
business logic, or evaluate a machine learned search ranking model. Grouping is 
done by field values, in a set of hierarchical groups, where each group can 
contain aggregated values over the data in the group. Grouping can be combined 
with aggregation to calculate values for, e.g., navigation aids, tag clouds, 
graphs or for clustering — all in a distributed fashion, without having to ship 
all the data back up to a container cluster, which is prohibitively expensive 
with large data sets.
- Data dumps: Content matching some criterion can be streamed out for background 
reprocessing, backup, etc., by using the visit operation.
- Any other custom network request which can be handled by application components 
deployed on a container cluster.

These operations allow developers to build feature rich applications expressed 
as Java middleware logic working on stored content, where selection, keyword 
searches and organization and processing of the content can be expressed as 
declarative queries by the middleware logic.

---

## Installation

### List of tools

This is the list of tools a developer needs to install locally to work with Vespa:

- Docker desktop
- Git
- Minimum 6GB memory dedicated to Docker (the default is 2GB on Macs) & 10G for monitoring section


### Setup Vespa locally

Follow the next steps to setup Vespa locally:

- Insert a new container that defines Vespa details inside the `docker-compose.yaml` file
- Add the new package called **vespa** in your Golang project
- Create the `/application` folder
- Define the configuration files `hosts.xml` & `services.xml`
- Include the `/schema` folder and create the definition of the document `<document name>.sd`
- Run the next command to initialize the Vespa container:
```
$ docker-compose up -d <vespa container name>
```


### Connect to Vespa Cloud

- Create an application in the Vespa Cloud

- Create a self-signed certificate for the local application source 
(If you didn't add it when you create the local application)
- Deploy the application
- Verify that you can reach the application endpoint
- Store data and run queries using the **/document/v1 api** 

For more details: 
- [Getting Started with the Vespa Cloud](https://cloud.vespa.ai/en/getting-started)
- [/document/v1 API](https://docs.vespa.ai/en/reference/document-v1-api-reference.html)
- [Query API](https://docs.vespa.ai/en/reference/query-api-reference.html)

---

