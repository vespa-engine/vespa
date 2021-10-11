package main

import (
	"context"
	"fmt"

	vespa "github.com/vespa-engine/vespa/vespaclient-go"
)

func main() {

	ctx := context.Background()

	client, err := vespa.NewClient(ctx)
	if err != nil {
		panic(err)
	}

	svc := vespa.NewCreateService(client)

	data := job{
		ID:          "abc1234",
		Title:       "Book Keeper",
		Description: "Culpa dolor Lorem magna laboris exercitation cillum adipisicing. Ea veniam excepteur et ex velit non commodo id incididunt mollit cupidatat quis. Laborum pariatur aliqua occaecat nulla reprehenderit irure ut pariatur consequat cillum. Elit excepteur cupidatat labore eiusmod cillum reprehenderit labore. Aliquip exercitation irure ut consequat. Et dolore esse nostrud do anim dolore voluptate laboris magna incididunt pariatur.",
	}

	resCreate, err := svc.Scheme("job").
		ID("abc1234").
		Body(data).
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Insert response: %+v", resCreate)

	uSvc := vespa.NewUpdateService(client)

	resUpdate, err := uSvc.Scheme("job").
		ID("abc1234").
		Field("title", "Chief officer").
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Update response: %+v", resUpdate)

	// You can also use selection to update multiple documents.
	// For the request to work, you need to fill the cluster name

	uSvc = vespa.NewUpdateService(client)
	_, err = uSvc.Scheme("job").
		Field("title", "Chief officer").
		Selection("external_id = ?", "zyx2345").
		Cluster("cluster-names").
		Do(ctx)
	if err != nil {
		panic(err)
	}
}

type job struct {
	ID          string `json:"id,omitempty"`
	Title       string `json:"title,omitempty"`
	Description string `json:"description,omitempty"`
}
