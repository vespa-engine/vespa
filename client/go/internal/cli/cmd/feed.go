package cmd

import (
	"encoding/json"
	"fmt"
	"io"
	"math"
	"os"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/document"
)

func addFeedFlags(cmd *cobra.Command, concurrency *int) {
	// TOOD(mpolden): Remove this flag
	cmd.PersistentFlags().IntVarP(concurrency, "concurrency", "T", 64, "Number of goroutines to use for dispatching")
}

func newFeedCmd(cli *CLI) *cobra.Command {
	var (
		concurrency int
	)
	cmd := &cobra.Command{
		Use:   "feed FILE",
		Short: "Feed documents to a Vespa cluster",
		Long: `Feed documents to a Vespa cluster.

A high performance feeding client. This can be used to feed large amounts of
documents to Vespa cluster efficiently.

The contents of FILE must be either a JSON array or JSON objects separated by
newline (JSONL).
`,
		Example: `$ vespa feed documents.jsonl
`,
		Args:              cobra.ExactArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Hidden:            true, // TODO(mpolden): Remove when ready for public use
		RunE: func(cmd *cobra.Command, args []string) error {
			f, err := os.Open(args[0])
			if err != nil {
				return err
			}
			defer f.Close()
			return feed(f, cli, concurrency)
		},
	}
	addFeedFlags(cmd, &concurrency)
	return cmd
}

func feed(r io.Reader, cli *CLI, concurrency int) error {
	service, err := documentService(cli)
	if err != nil {
		return err
	}
	client := document.NewClient(document.ClientOptions{
		BaseURL: service.BaseURL,
	}, service)
	throttler := document.NewThrottler()
	dispatcher := document.NewDispatcher(client, throttler)
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
