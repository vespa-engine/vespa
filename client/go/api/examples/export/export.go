package main

import (
	"context"
	"fmt"
	"net/http"
	"time"

	vespa "github.com/vespa-engine/vespa/vespaclient-go"
)

// The following is an example on how to achieve data export based on Search
// when high performance is needed.
// The main things to set when doing so are:
// - Disable ranking softtimeout
// - Disable sorting degrating
// - Set enough timeout

func main() {
	ctx := context.Background()

	httpTransport := &http.Transport{
		DisableKeepAlives:   false,
		MaxIdleConnsPerHost: 10,
	}

	httpClient := &http.Client{
		Transport: httpTransport,
		Timeout:   25 * time.Second,
	}

	var opts []vespa.ClientOptionFunc

	connStr := "http://localhost"

	opts = append(opts,
		vespa.SetHTTPClient(httpClient),
		vespa.SetURL(connStr),
	)

	client, err := vespa.NewClient(ctx, opts...)
	if err != nil {
		panic(err)
	}

	svc := vespa.NewSearchService(client)

	degrating := false

	res, err := svc.Scheme("job").
		Where("weightedSet(?, ?)", "feed_id", []vespa.WeightedElement{
			{Index: "16449", Value: 1}, {Index: "34558", Value: 1},
		}).
		Ranking(vespa.Ranking{
			SoftTimeout: &vespa.SoftTimeout{
				Enabled: false,
			},
		}).
		Sorting(
			vespa.Sorting{
				Degrading: &degrating,
			},
		).
		Timeout("10s").
		Limit(400).
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("%+v\n", res)

}
