package main

import (
	"context"
	"fmt"

	vespa "github.com/vespa-engine/vespa/vespaclient-go"
)

func main() {
	// Create a context
	ctx := context.Background()

	// Create a new Vespa client
	client, err := vespa.NewClient(ctx)
	if err != nil {
		panic(err)
	}

	// Create the Visit Service
	svc := vespa.NewVisitService(client)

	// Define a selection
	res, err := svc.Scheme("job").
		Selection("job.status == ?", "active").
		WantedDocumentCount(1000).
		Do(ctx)
	if err != nil {
		panic(err)
	}

	// Verify if there's a failed message
	if res.Message != "" {
		// Print result
		fmt.Printf("Error Message: %+v\n", res)
	}

	fmt.Printf("Result: %+v", res)

	// Create a new Visit Service
	svc = vespa.NewVisitService(client)

	// Define a continuation
	res, err = svc.Scheme("job").
		Continuation("AAAAEAAAAAAAAAM3AAAAAAAAAzYAAAAAAAEAAAAAAAFAAAAAAABswAAAAAAAAAAA").
		Do(ctx)
	if err != nil {
		panic(err)
	}

	// Verify if there's a failed message
	if res.Message != "" {
		// Print result
		fmt.Printf("Error Message: %+v\n", res)
	}

	fmt.Printf("Result using the continuation token: %+v", res)
}
