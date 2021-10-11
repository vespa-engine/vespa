package main

import (
	"context"
	"fmt"
	"net/http"

	vespa "github.com/vespa-engine/vespa/vespaclient-go"
)

func main() {

	ctx := context.Background()

	hClient := http.Client{
		Transport: &http.Transport{
			MaxIdleConns: 20,
		},
	}

	client, err := vespa.NewClient(ctx, vespa.SetHTTPClient(&hClient))
	if err != nil {
		panic(err)
	}

	svc := vespa.NewBatchService(client)

	for _, job := range ManyJobs(1000) {
		item := vespa.NewCreateBatchRequest().
			Namespace("default").
			Scheme("job").
			ID(job.ID).
			Body(job)

		svc.Add(item)
	}

	res := svc.Do(ctx)

	fmt.Printf("%+v\n", res)

}

func Jobs() []job {
	data := []job{
		{
			ID:    "11223344",
			Title: "Sr Software Engineer",
		},
		{
			ID:    "11223345",
			Title: "Doctor",
		},
		{
			ID:    "11223346",
			Title: "Web Developer",
		},
		{
			ID:    "11223347",
			Title: "Mobile Developer",
		},
		{
			ID:    "11223348",
			Title: "Backend Engineer",
		},
	}

	return data
}

func ManyJobs(max int) []job {
	var data []job

	for i := 0; i < max; i++ {
		item := job{
			ID: fmt.Sprintf("sample-%d", i+1),

			Title: "Sr Software Engineer",
		}
		data = append(data, item)
	}

	return data
}

type job struct {
	ID    string `json:"id,omitempty"`
	Title string `json:"title,omitempty"`
}
