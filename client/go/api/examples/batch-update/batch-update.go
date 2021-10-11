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

	for _, job := range ManyJobs(20) {
		item := vespa.NewCreateBatchRequest().
			Namespace("default").
			Scheme("job").
			ID(job.ID).
			Body(job)

		svc.Add(item)
	}

	insertRes := svc.Do(ctx)

	fmt.Printf("Inserted documents: %d\n", len(insertRes.Items))

	svc = vespa.NewBatchService(client)

	for _, job := range insertRes.Items {
		item := vespa.NewUpdateBatchRequest().
			Namespace("default").
			Scheme("job").
			ID(job.ID).
			Field("hidden", true)

		svc.Add(item)
	}

	updateRes := svc.Do(ctx)

	fmt.Printf("Updated documents: %d\n", len(insertRes.Items))
	fmt.Printf("%+v\n", updateRes)

}

// ManyJobs creates a jobs array and it includes
// so many items as we defined in the "max" parameter.
func ManyJobs(max int) []job {
	var data []job

	for i := 0; i < max; i++ {
		item := job{
			ID:    fmt.Sprintf("sample-%d", i+1),
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
