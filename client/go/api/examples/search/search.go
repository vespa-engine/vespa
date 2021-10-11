package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
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
		RootCAs:            caCertPool,
		Certificates:       []tls.Certificate{cert},
		InsecureSkipVerify: true, // for development only
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
	weak := vespa.NewWeakAnd(10)
	weak.AddEntry("default", "Trucking")
	weak.AddEntry("default", "Class")
	weak.AddEntry("default", "B")
	weak.AddEntry("default", "Truck")
	weak.AddEntry("default", "Driving")

	res, err := svc.Scheme("job").
		Where("? AND status contains \"active\" AND geoLocation(location, 33.64, -84.33, \"25mi\") AND hidden = false", *weak).
		Timeout("5s").
		Ranking(vespa.Ranking{Profile: "bm25"}).
		// RankingFeatureQuery(vespa.RankingFeaturesQuery{
		// 	Values: map[string]interface{}{
		// 		"cpcfactor":       1,
		// 		"closenessfactor": 20,
		// 	},
		// }).
		Hits(10).
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Number of matches: %d \n", res.Root.Fields.TotalCount)

	for i, item := range res.Root.Children {
		vespaJob := JobRes{}
		// Decoder the result into the new job object
		err = json.Unmarshal(res.Root.Children[i].Fields, &vespaJob)
		// Check if the unmarshal operation has an error
		if err != nil {
			panic(err)
		}
		fmt.Printf("Title: %s, Relevance: %f\n", vespaJob.Title, item.Relevance)
	}

	fmt.Println("---------------")
	fmt.Println("USING ALL MODE!")

	svc2 := vespa.NewSearchService(client)
	res, err = svc2.Scheme("job").
		Query("Trucking Class B Truck Driving").
		Where("userQuery() AND status contains \"active\" AND geoLocation(location, 33.64, -84.33, \"25mi\") AND hidden = false").
		Timeout("5s").
		Ranking(vespa.Ranking{Profile: "bm25"}).
		// RankingFeatureQuery(vespa.RankingFeaturesQuery{
		// 	Values: map[string]interface{}{
		// 		"cpcfactor":       1,
		// 		"closenessfactor": 20,
		// 	},
		// }).
		// UseWeakAndQuery(*weak).
		Hits(10).
		Do(ctx)
	if err != nil {
		panic(err)
	}

	fmt.Printf("Number of matches: %d \n", res.Root.Fields.TotalCount)

	for i, item := range res.Root.Children {
		vespaJob := JobRes{}
		// Decoder the result into the new job object
		err = json.Unmarshal(res.Root.Children[i].Fields, &vespaJob)
		// Check if the unmarshal operation has an error
		if err != nil {
			panic(err)
		}
		fmt.Printf("Title: %s Relevance: %f\n", vespaJob.Title, item.Relevance)
	}

}

type JobRes struct {
	ID               string           `json:"id,omitempty"`
	Title            string           `json:"title,omitempty"`
	Description      string           `json:"description,omitempty"`
	DescriptionFull  string           `json:"descriptionfull,omitempty"`
	Location         LocationPosition `json:"location,omitempty"`
	LocationPosition LocationPosition `json:"location.position,omitempty"`
}

// LocationPosition is the representation Vespa returns
// when a record has a location field set.
type LocationPosition struct {
	X      float64 `json:"x,omitempty"`
	Y      float64 `json:"y,omitempty"`
	LatLon string  `json:"latlon,omitempty"`
}
