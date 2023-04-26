package cmd

import (
	"encoding/json"
	"fmt"
	"io"
	"math"
	"os"
	"runtime/pprof"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/util"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/document"
)

func addFeedFlags(cmd *cobra.Command, options *feedOptions) {
	cmd.PersistentFlags().IntVar(&options.connections, "connections", 8, "The number of connections to use")
	cmd.PersistentFlags().StringVar(&options.compression, "compression", "auto", `Compression mode to use. Default is "auto" which compresses large documents. Must be "auto", "gzip" or "none"`)
	cmd.PersistentFlags().IntVar(&options.timeoutSecs, "timeout", 0, "Invididual feed operation timeout in seconds. 0 to disable")
	cmd.PersistentFlags().IntVar(&options.doomSecs, "max-failure-seconds", 0, "Exit if given number of seconds elapse without any successful operations. 0 to disable")
	cmd.PersistentFlags().BoolVar(&options.verbose, "verbose", false, "Verbose mode. Print successful operations in addition to errors")
	cmd.PersistentFlags().StringVar(&options.route, "route", "", "Target Vespa route for feed operations")
	cmd.PersistentFlags().IntVar(&options.traceLevel, "trace", 0, "The trace level of network traffic. 0 to disable")
	memprofile := "memprofile"
	cpuprofile := "cpuprofile"
	cmd.PersistentFlags().StringVar(&options.memprofile, memprofile, "", "Write a heap profile to given file")
	cmd.PersistentFlags().StringVar(&options.cpuprofile, cpuprofile, "", "Write a CPU profile to given file")
	// Hide these flags as they are intended for internal use
	cmd.PersistentFlags().MarkHidden(memprofile)
	cmd.PersistentFlags().MarkHidden(cpuprofile)
}

type feedOptions struct {
	connections int
	compression string
	route       string
	verbose     bool
	traceLevel  int
	timeoutSecs int
	doomSecs    int

	memprofile string
	cpuprofile string
}

func newFeedCmd(cli *CLI) *cobra.Command {
	var options feedOptions
	cmd := &cobra.Command{
		Use:   "feed FILE [FILE]...",
		Short: "Feed documents to a Vespa cluster",
		Long: `Feed documents to a Vespa cluster.

This command can be used to feed large amounts of documents to a Vespa cluster
efficiently.

The contents of FILE must be either a JSON array or JSON objects separated by
newline (JSONL).

If FILE is a single dash ('-'), documents will be read from standard input.
`,
		Example: `$ vespa feed docs.jsonl moredocs.json
$ cat docs.jsonl | vespa feed -`,
		Args:              cobra.MinimumNArgs(1),
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		Hidden:            true, // TODO(mpolden): Remove when ready for public use
		RunE: func(cmd *cobra.Command, args []string) error {
			if options.cpuprofile != "" {
				f, err := os.Create(options.cpuprofile)
				if err != nil {
					return err
				}
				pprof.StartCPUProfile(f)
				defer pprof.StopCPUProfile()
			}
			err := feed(args, options, cli)
			if options.memprofile != "" {
				f, err := os.Create(options.memprofile)
				if err != nil {
					return err
				}
				defer f.Close()
				pprof.WriteHeapProfile(f)
			}
			return err
		},
	}
	addFeedFlags(cmd, &options)
	return cmd
}

func createServiceClients(service *vespa.Service, n int) []util.HTTPClient {
	clients := make([]util.HTTPClient, 0, n)
	for i := 0; i < n; i++ {
		client := service.Client().Clone()
		// Feeding should always use HTTP/2
		util.ForceHTTP2(client, service.TLSOptions.KeyPair, service.TLSOptions.CACertificate, service.TLSOptions.TrustAll)
		clients = append(clients, client)
	}
	return clients
}

func (opts feedOptions) compressionMode() (document.Compression, error) {
	switch opts.compression {
	case "auto":
		return document.CompressionAuto, nil
	case "none":
		return document.CompressionNone, nil
	case "gzip":
		return document.CompressionGzip, nil
	}
	return 0, errHint(fmt.Errorf("invalid compression mode: %s", opts.compression), `Must be "auto", "gzip" or "none"`)
}

func feed(files []string, options feedOptions, cli *CLI) error {
	service, err := documentService(cli)
	if err != nil {
		return err
	}
	clients := createServiceClients(service, options.connections)
	compression, err := options.compressionMode()
	if err != nil {
		return err
	}
	client := document.NewClient(document.ClientOptions{
		Compression: compression,
		Timeout:     time.Duration(options.timeoutSecs) * time.Second,
		Route:       options.route,
		TraceLevel:  options.traceLevel,
		BaseURL:     service.BaseURL,
		NowFunc:     cli.now,
	}, clients)
	throttler := document.NewThrottler(options.connections)
	circuitBreaker := document.NewCircuitBreaker(10*time.Second, time.Duration(options.doomSecs)*time.Second)
	dispatcher := document.NewDispatcher(client, throttler, circuitBreaker, cli.Stderr, options.verbose)
	start := cli.now()
	for _, name := range files {
		var r io.ReadCloser
		if len(files) == 1 && name == "-" {
			r = io.NopCloser(cli.Stdin)
		} else {
			f, err := os.Open(name)
			if err != nil {
				return err
			}
			r = f
		}
		dec := document.NewDecoder(r)
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
		r.Close()
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
