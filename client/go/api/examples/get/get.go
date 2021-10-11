package main

import (
	"context"
	"encoding/json"
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

	// Create the Get Service
	svc := vespa.NewGetService(client)

	// Get the document by ID
	res, err := svc.Scheme("job").
		ID("abc1234").
		Do(ctx)
	if err != nil {
		panic(err)
	}

	// Verify if there's a retrieved document
	if res.Fields != nil {
		// Declare struct map
		doc := &job{}

		// Decode the json.RawMessage
		err = json.Unmarshal(res.Fields, doc)
		if err != nil {
			panic(err)
		}

		// Print the document
		fmt.Printf("%+v\n", doc)
	}

	// Verify if there's a failed message
	if res.Message != "" {
		// Print result
		fmt.Printf("%+v\n", res)
	}
}

// job attribute contains the fields of the documents
type job struct {
	ID          string   `json:"id,omitempty"`
	Title       string   `json:"title,omitempty"`
	Description string   `json:"description,omitempty"`
	Location    Location `json:"location,omitempty"`
}

// Location attribute contains the detailed position of a physical location
type Location struct {
	Lat float64 `json:"y,omitempty"`
	Lon float64 `json:"x,omitempty"`
}
