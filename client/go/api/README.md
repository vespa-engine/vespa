# Vespa-Go

Vespa-Go is a Client for [Vespa](https://vespa.ai/) heavily inspired by [olivere/elastic](https://github.com/olivere/elastic).

## Motivation

# Business

## Features

## Technology Stack

# Technical

## Getting Started

To install the Vespa-Go package, you can run the following:

1. Install:
```
$ go get -u github.com/vespa-engine/vespa/vespaclient-go
```

2. Import in your code:

```
import github.com/vespa-engine/vespa/vespaclient-go
```

The minimun go version we have tried is GO 1.14

---

### Quick Start

```
$ cat quickstart.go
```

```Go
// vespa.go

package main

import github.com/vespa-engine/vespa/vespaclient-go

func main() {
	// Create a context
	ctx := context.Background()

	// Create a new Vespa client
	client, err := vespa.NewClient(ctx)
	if err != nil {
		panic(err)
	}

	// Create the Get Service
	svc := vespa.NewGetService(client)

	// Get the document by ID
	res, err := svc.Scheme("job").
		ID("abc1234").
		Do(ctx)
	if err != nil {
		panic(err)
	}

	// Note that we leave as you the user the step
	// to decode the paylod of *res.Fields* to which ever structure
	// you need.
}

```

More examples can be found over the [example](./examples/) folder

## Development

## Testing

### **Unit tests**

### **Integration tests**

### **Code analysis**

## Deployment

## Structure of the repository

## Code style

## APIs

The library aims to implement all the operations exposed as API by Vespa.

Logically, the operations are divided by services, where service represents a identificable operation:

- **CreateService**: Used to create or update documents
- **UpdateService**: Used to *partially* update documents
- **DeleteService**: Used to delete documents
- **GetService**: Used to retrieve documents by ID
- **BatchService**: Used to execute operation such as the ones above, but making multiple requests at the same time using goroutines.
- **SearchService**: Used to search for documents using YQL
- **VisitService**: Used to iterate over and get all documents, or a selection of documents, in chunks, using continuation tokens to track progress.

In the future, the batch request will make use of the Async API in order to make a single Request.

For example, in order to BatchDelete documents you could:

```Go
// delete.go

package main

import github.com/vespa-engine/vespa/vespaclient-go

func main() {
	// Create a context
	ctx := context.Background()

	// Create a new Vespa client
	client, err := vespa.NewClient(ctx)
	if err != nil {
		panic(err)
	}

	// Create the Get Service
	svc := vespa.NewDeleteService(client)

	resDelete, err := dSvc.Scheme("job").
		Selection("job.external_id = ?", "zyx2345").
		Cluster("job").
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Delete response: %+v\n", resDelete)
}
```
> **Warning**:
> The current API is not considered stable and it is subject to change. However, we hope to stick to semantic versioning in order to keep changes as stable as possible.

## Logging and error handling

### **Logging**

### **Error handling**

## Diagrams/Visuals

## Packages

# External Resources
