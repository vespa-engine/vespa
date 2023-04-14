package cmd

import (
	"encoding/json"
	"fmt"
	"io"
	"math"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/document"
)

func addFeedFlags(cmd *cobra.Command, options *feedOptions) {
	cmd.PersistentFlags().IntVar(&options.connections, "connections", 8, "The number of connections to use")
	cmd.PersistentFlags().StringVar(&options.route, "route", "", "Target Vespa route for feed operations")
	cmd.PersistentFlags().IntVar(&options.traceLevel, "trace", 0, "The trace level of network traffic. 0 to disable")
	cmd.PersistentFlags().IntVar(&options.timeoutSecs, "timeout", 0, "Feed operation timeout in seconds. 0 to disable")
	cmd.PersistentFlags().BoolVar(&options.verbose, "verbose", false, "Verbose mode. Print errors as they happen")
}

type feedOptions struct {
	connections int
	route       string
	verbose     bool
	traceLevel  int
	timeoutSecs int
}

func newFeedCmd(cli *CLI) *cobra.Command {
	var options feedOptions
	cmd := &cobra.Command{
		Use:   "feed FILE",
		Short: "Feed documents to a Vespa cluster",
		Long: `Feed documents to a Vespa cluster.

A high performance feeding client. This can be used to feed large amounts of
documents to a Vespa cluster efficiently.

The contents of FILE must be either a JSON array or JSON objects separated by
newline (JSONL).

If FILE is a single dash ('-'), documents will be read from standard input.
`,
		Example: `$ vespa feed documents.jsonl
$ cat documents.jsonl | vespa feed -
`,
		Args:              cobra.ExactArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Hidden:            true, // TODO(mpolden): Remove when ready for public use
		RunE: func(cmd *cobra.Command, args []string) error {
			var r io.Reader
			if args[0] == "-" {
				r = cli.Stdin
			} else {
				f, err := os.Open(args[0])
				if err != nil {
					return err
				}
				defer f.Close()
				r = f
			}
			return feed(r, cli, options)
		},
	}
	addFeedFlags(cmd, &options)
	return cmd
}

func createServiceClients(service *vespa.Service, n int) []util.HTTPClient {
	clients := make([]util.HTTPClient, 0, n)
	for i := 0; i < n; i++ {
		client := service.Client().Clone()
		util.ForceHTTP2(client, service.TLSOptions.KeyPair) // Feeding should always use HTTP/2
		clients = append(clients, client)
	}
	return clients
}

func feed(r io.Reader, cli *CLI, options feedOptions) error {
	service, err := documentService(cli)
	if err != nil {
		return err
	}
	clients := createServiceClients(service, options.connections)
	client := document.NewClient(document.ClientOptions{
		Timeout:    time.Duration(options.timeoutSecs) * time.Second,
		Route:      options.route,
		TraceLevel: options.traceLevel,
		BaseURL:    service.BaseURL,
	}, clients)
	throttler := document.NewThrottler(options.connections)
	// TODO(mpolden): Make doom duration configurable
	circuitBreaker := document.NewCircuitBreaker(10*time.Second, 0)
	errWriter := io.Discard
	if options.verbose {
		errWriter = cli.Stderr
	}
	dispatcher := document.NewDispatcher(client, throttler, circuitBreaker, errWriter)
	dec := document.NewDecoder(r)

	start := cli.now()
	for {
		doc, err := dec.Decode()
		if err == io.EOF {
			break
		}
		if err != nil {
			cli.printErr(fmt.Errorf("failed to decode document: %w", err))
		}
		if err := dispatcher.Enqueue(doc); err != nil {
			cli.printErr(err)
		}
	}
	if err := dispatcher.Close(); err != nil {
		return err
	}
	elapsed := cli.now().Sub(start)
	return writeSummaryJSON(cli.Stdout, dispatcher.Stats(), elapsed)
}

type number float32

func (n number) MarshalJSON() ([]byte, error) { return []byte(fmt.Sprintf("%.3f", n)), nil }

type feedSummary struct {
	Seconds       number `json:"feeder.seconds"`
	SuccessCount  int64  `json:"feeder.ok.count"`
	SuccessRate   number `json:"feeder.ok.rate"`
	ErrorCount    int64  `json:"feeder.error.count"`
	InflightCount int64  `json:"feeder.inflight.count"`

	RequestCount   int64  `json:"http.request.count"`
	RequestBytes   int64  `json:"http.request.bytes"`
	RequestRate    number `json:"http.request.MBps"`
	ExceptionCount int64  `json:"http.exception.count"` // same as ErrorCount, for compatability with vespa-feed-client

	ResponseCount      int64  `json:"http.response.count"`
	ResponseBytes      int64  `json:"http.response.bytes"`
	ResponseRate       number `json:"http.response.MBps"`
	ResponseErrorCount int64  `json:"http.response.error.count"`

	ResponseMinLatency int64         `json:"http.response.latency.millis.min"`
	ResponseAvgLatency int64         `json:"http.response.latency.millis.avg"`
	ResponseMaxLatency int64         `json:"http.response.latency.millis.max"`
	ResponseCodeCounts map[int]int64 `json:"http.response.code.counts"`
}

func mbps(bytes int64, duration time.Duration) float64 {
	return (float64(bytes) / 1000 / 1000) / math.Max(1, duration.Seconds())
}

func writeSummaryJSON(w io.Writer, stats document.Stats, duration time.Duration) error {
	summary := feedSummary{
		Seconds:       number(duration.Seconds()),
		SuccessCount:  stats.Successes(),
		SuccessRate:   number(float64(stats.Successes()) / math.Max(1, duration.Seconds())),
		ErrorCount:    stats.Errors,
		InflightCount: stats.Inflight,

		RequestCount:   stats.Requests,
		RequestBytes:   stats.BytesSent,
		RequestRate:    number(mbps(stats.BytesSent, duration)),
		ExceptionCount: stats.Errors,

		ResponseCount:      stats.Responses,
		ResponseBytes:      stats.BytesRecv,
		ResponseRate:       number(mbps(stats.BytesRecv, duration)),
		ResponseErrorCount: stats.Responses - stats.Successes(),
		ResponseMinLatency: stats.MinLatency.Milliseconds(),
		ResponseAvgLatency: stats.AvgLatency().Milliseconds(),
		ResponseMaxLatency: stats.MaxLatency.Milliseconds(),
		ResponseCodeCounts: stats.ResponsesByCode,
	}
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	return enc.Encode(summary)
}
