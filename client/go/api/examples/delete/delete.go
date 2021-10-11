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
		ID:    "abc1234",
		Title: "Book Keeper",
	}

	resCreate, err := svc.Scheme("job").
		ID("abc1234").
		Body(data).
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Insert response: %+v\n", resCreate)

	dSvc := vespa.NewDeleteService(client)
	resDelete, err := dSvc.Scheme("job").
		Selection("job.id = ?", "zyx2345").
		Cluster("job").
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Delete response: %+v\n", resDelete)
}

type job struct {
	ID    string `json:"id,omitempty"`
	Title string `json:"title,omitempty"`
}
