package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"time"

	vespa "github.com/vespa-engine/vespa/vespaclient-go"
)

func main() {
	ctx := context.Background()

	httpTransport := &http.Transport{
		DisableKeepAlives:   false,
		MaxIdleConnsPerHost: 10,
	}

	// Setup mTLS
	cert, err := tls.LoadX509KeyPair("client.crt", "client.key")
	if err != nil {
		panic(err)
	}

	// Create a CA certificate pool and add cert.pem to it
	caCert, err := ioutil.ReadFile("client.crt")
	if err != nil {
		log.Fatal(err)
	}
	caCertPool := x509.NewCertPool()
	caCertPool.AppendCertsFromPEM(caCert)

	httpTransport.TLSClientConfig = &tls.Config{
		RootCAs:      caCertPool,
		Certificates: []tls.Certificate{cert},
		// TODO: this should be false in production. Need to find a way to make it work
		InsecureSkipVerify: true,
	}

	httpClient := &http.Client{
		Transport: httpTransport,
		Timeout:   25 * time.Second,
	}

	var opts []vespa.ClientOptionFunc

	connStr := "http://localhost:8080"

	opts = append(opts,
		vespa.SetHTTPClient(httpClient),
		vespa.SetURL(connStr),
	)

	client, err := vespa.NewClient(ctx, opts...)
	if err != nil {
		panic(err)
	}

	client.GzipRequest = true

	svc := vespa.NewSearchService(client)

	res, err := svc.Scheme("job").
		Query("Software Engineer").
		Where("userQuery() AND status contains \"active\" AND geoLocation(location, 33.64, -84.33, \"100mi\") AND hidden = false").
		Timeout("5s").
		Ranking(vespa.Ranking{Profile: "bm25"}).
		Hits(10).
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Number of matches: %d \n", res.Root.Fields.TotalCount)

}

type JobRes struct {
	ID          string `json:"id,omitempty"`
	Title       string `json:"title,omitempty"`
	Description string `json:"description,omitempty"`
}

// LocationPosition is the representation Vespa returns
// when a record has a location field set.
type LocationPosition struct {
	X      float64 `json:"x,omitempty"`
	Y      float64 `json:"y,omitempty"`
	LatLon string  `json:"latlon,omitempty"`
}
