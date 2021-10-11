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
		Title:       "Sr Software Engineer",
		Description: "Senior Software Engineer provides innovative, simple solutions that allows the stores and other business partners to operate more efficiently while driving profitability and sustainability through the Vendor-DC-Store supply chain network. Supports the store allocation of associate labor through accurate engineered labor standards. Responsible for new and remodel store layout/design and new equipment and processes to support operational efficiency and damage reduction .",
	}

	res, err := svc.Scheme("job").
		ID("abc1234").
		Body(data).
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("%+v\n", res)
}

type job struct {
	ID          string `json:"id,omitempty"`
	Title       string `json:"title,omitempty"`
	Description string `json:"description,omitempty"`
}
