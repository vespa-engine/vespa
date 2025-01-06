// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"os"
	"runtime/pprof"
	"time"

	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/httputil"
	"github.com/vespa-engine/vespa/client/go/internal/vespa/document"
)

func addFeedFlags(cli *CLI, cmd *cobra.Command, options *feedOptions) {
	cmd.PersistentFlags().IntVar(&options.connections, "connections", 8, "The number of connections to use")
	cmd.PersistentFlags().IntVar(&options.inflight, "inflight", 0, "The target number of inflight requests. 0 to dynamically detect the best value (default 0)")
	cmd.PersistentFlags().StringVar(&options.compression, "compression", "auto", `Whether to compress the document data when sending the HTTP request. Default is "auto", which compresses large documents. Must be "auto", "gzip" or "none"`)
	cmd.PersistentFlags().IntVar(&options.timeoutSecs, "timeout", 0, "Individual feed operation timeout in seconds. 0 to disable (default 0)")
	cmd.Flags().StringSliceVarP(&options.headers, "header", "", nil, "Add a header to all HTTP requests, on the format 'Header: Value'. This can be specified multiple times")
	cmd.PersistentFlags().IntVar(&options.doomSecs, "deadline", 0, "Exit if this number of seconds elapse without any successful operations. 0 to disable (default 0)")
	cmd.PersistentFlags().BoolVar(&options.verbose, "verbose", false, "Verbose mode. Print successful operations in addition to errors")
	cmd.PersistentFlags().StringVar(&options.route, "route", "", `Target Vespa route for feed operations (default "default")`)
	cmd.PersistentFlags().IntVar(&options.traceLevel, "trace", 0, "Network traffic trace level in the range [0,9]. 0 to disable (default 0)")
	cmd.PersistentFlags().IntVar(&options.summarySecs, "progress", 0, "Print stats summary at given interval, in seconds. 0 to disable (default 0)")
	cmd.PersistentFlags().IntVar(&options.speedtestBytes, "speedtest", 0, "Perform a network speed test using given payload, in bytes. 0 to disable (default 0)")
	cmd.PersistentFlags().IntVar(&options.speedtestSecs, "speedtest-duration", 60, "Duration of speedtest, in seconds")
	memprofile := "memprofile"
	cpuprofile := "cpuprofile"
	cmd.PersistentFlags().StringVar(&options.memprofile, memprofile, "", "Write a heap profile to given file")
	cmd.PersistentFlags().StringVar(&options.cpuprofile, cpuprofile, "", "Write a CPU profile to given file")
	// Hide these flags as they are intended for internal use
	cmd.PersistentFlags().MarkHidden(memprofile)
	cmd.PersistentFlags().MarkHidden(cpuprofile)
	cli.bindWaitFlag(cmd, 0, &options.waitSecs)
}

type feedOptions struct {
	connections    int
	inflight       int
	compression    string
	route          string
	verbose        bool
	traceLevel     int
	timeoutSecs    int
	doomSecs       int
	summarySecs    int
	speedtestBytes int
	speedtestSecs  int
	waitSecs       int
	headers        []string

	memprofile string
	cpuprofile string
}

func newFeedCmd(cli *CLI) *cobra.Command {
	var options feedOptions
	cmd := &cobra.Command{
		Use:   "feed json-file [json-file]...",
		Short: "Feed multiple document operations to Vespa",
		Long: `Feed multiple document operations to Vespa.

This command can be used to feed large amounts of documents to a Vespa cluster
efficiently.

The contents of json-file must be either a JSON array or JSON objects separated by
newline (JSONL).

If json-file is a single dash ('-'), documents will be read from standard input.

Once feeding completes, metrics of the feed session are printed to standard out
in a JSON format:

- feeder.operation.count: Number of operations passed to the feeder by the user,
  not counting retries.
- feeder.seconds: Total time spent feeding.
- feeder.ok.count: Number of successful operations.
- feeder.ok.rate: Number of successful operations per second.
- feeder.error.count: Number of network errors (transport layer).
- feeder.inflight.count: Number of operations currently being sent.
- http.request.count: Number of HTTP requests made, including retries.
- http.request.bytes: Number of bytes sent.
- http.request.MBps: Request throughput measured in MB/s. This is the raw
  operation throughput, and not the network throughput,
  I.e. using compression does not affect this number.
- http.exception.count: Same as feeder.error.count. Present for compatibility
  with vespa-feed-client.
- http.response.count: Number of HTTP responses received.
- http.response.bytes: Number of bytes received.
- http.response.MBps: Response throughput measured in MB/s.
- http.response.error.count: Number of non-OK HTTP responses received.
- http.response.latency.millis.min: Lowest latency of a successful operation.
- http.response.latency.millis.avg: Average latency of successful operations.
- http.response.latency.millis.max: Highest latency of a successful operation.
- http.response.code.counts: Number of responses grouped by their HTTP code.
`,
		Example: `$ vespa feed docs.jsonl moredocs.json
$ cat docs.jsonl | vespa feed -`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			if options.cpuprofile != "" {
				f, err := os.Create(options.cpuprofile)
				if err != nil {
					return err
				}
				pprof.StartCPUProfile(f)
				defer pprof.StopCPUProfile()
			}
			err := feed(args, options, cli, cmd)
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
	addFeedFlags(cli, cmd, &options)
	return cmd
}

func createServices(n int, timeout time.Duration, cli *CLI, waiter *Waiter) ([]httputil.Client, string, error) {
	if n < 1 {
		return nil, "", fmt.Errorf("need at least one client")
	}
	target, err := cli.target(targetOptions{})
	if err != nil {
		return nil, "", err
	}
	services := make([]httputil.Client, 0, n)
	baseURL := ""
	for range n {
		service, err := waiter.Service(target, cli.config.cluster())
		if err != nil {
			return nil, "", err
		}
		baseURL = service.BaseURL
		// Create a separate HTTP client for each service
		client := cli.httpClientFactory(timeout)
		// Feeding should always use HTTP/2
		httputil.ForceHTTP2(client, service.TLSOptions.KeyPair, service.TLSOptions.CACertificatePEM, service.TLSOptions.TrustAll)
		service.SetClient(client)
		services = append(services, service)
	}
	return services, baseURL, nil
}

func summaryTicker(secs int, cli *CLI, start time.Time, statsFunc func() document.Stats) *time.Ticker {
	if secs < 1 {
		return nil
	}
	ticker := time.NewTicker(time.Duration(secs) * time.Second)
	go func() {
		for range ticker.C {
			writeSummaryJSON(cli.Stderr, statsFunc(), cli.now().Sub(start))
		}
	}()
	return ticker
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

func enqueueFromFiles(files []string, dispatcher *document.Dispatcher, cli *CLI) error {
	for _, name := range files {
		var r io.ReadCloser
		if len(files) == 1 && name == "-" {
			r = io.NopCloser(cli.Stdin)
		} else {
			f, err := os.Open(name)
			if err != nil {
				cli.printErr(err)
				continue
			}
			r = f
		}
		if err := enqueueFrom(r, dispatcher, cli); err != nil {
			return err
		}
	}
	return nil
}

func enqueueFrom(r io.ReadCloser, dispatcher *document.Dispatcher, cli *CLI) error {
	dec := document.NewDecoder(bufio.NewReaderSize(r, 1<<26)) // Buffer up to 64M of data at a time
	defer r.Close()
	for {
		doc, err := dec.Decode()
		if err == io.EOF {
			break
		}
		if err != nil {
			return fmt.Errorf("failed to decode document: %w", err)
		}
		if err := dispatcher.Enqueue(doc); err != nil {
			return err
		}
	}
	return nil
}

func enqueueAndWait(files []string, dispatcher *document.Dispatcher, options feedOptions, cli *CLI) error {
	defer dispatcher.Close()
	if options.speedtestBytes > 0 {
		if len(files) > 0 {
			return fmt.Errorf("option --speedtest cannot be combined with feed files")
		}
		gen := document.NewGenerator(options.speedtestBytes, cli.now().Add(time.Duration(options.speedtestSecs)*time.Second))
		return enqueueFrom(io.NopCloser(gen), dispatcher, cli)
	} else if len(files) > 0 {
		return enqueueFromFiles(files, dispatcher, cli)
	}
	return fmt.Errorf("at least one file to feed from must specified")
}

func feed(files []string, options feedOptions, cli *CLI, cmd *cobra.Command) error {
	timeout := time.Duration(options.timeoutSecs) * time.Second
	waiter := cli.waiter(time.Duration(options.waitSecs)*time.Second, cmd)
	clients, baseURL, err := createServices(options.connections, timeout, cli, waiter)
	if err != nil {
		return err
	}
	compression, err := options.compressionMode()
	if err != nil {
		return err
	}
	header, err := httputil.ParseHeader(options.headers)
	if err != nil {
		return err
	}
	client, err := document.NewClient(document.ClientOptions{
		Compression: compression,
		Timeout:     timeout,
		Route:       options.route,
		TraceLevel:  options.traceLevel,
		BaseURL:     baseURL,
		Header:      header,
		Speedtest:   options.speedtestBytes > 0,
		NowFunc:     cli.now,
	}, clients)
	if err != nil {
		return err
	}
	throttler := document.NewThrottler(options.connections, options.inflight)
	circuitBreaker := document.NewCircuitBreaker(10*time.Second, time.Duration(options.doomSecs)*time.Second)
	dispatcher := document.NewDispatcher(client, throttler, circuitBreaker, cli.Stderr, options.verbose)
	start := cli.now()
	summaryTicker := summaryTicker(options.summarySecs, cli, start, dispatcher.Stats)
	defer func() {
		if summaryTicker != nil {
			summaryTicker.Stop()
		}
		elapsed := cli.now().Sub(start)
		writeSummaryJSON(cli.Stdout, dispatcher.Stats(), elapsed)
	}()
	return enqueueAndWait(files, dispatcher, options, cli)
}

type number float32

func (n number) MarshalJSON() ([]byte, error) { return []byte(fmt.Sprintf("%.3f", n)), nil }

type feedSummary struct {
	Operations    int64  `json:"feeder.operation.count"`
	Seconds       number `json:"feeder.seconds"`
	SuccessCount  int64  `json:"feeder.ok.count"`
	SuccessRate   number `json:"feeder.ok.rate"`
	ErrorCount    int64  `json:"feeder.error.count"`
	InflightCount int64  `json:"feeder.inflight.count"`

	RequestCount   int64  `json:"http.request.count"`
	RequestBytes   int64  `json:"http.request.bytes"`
	RequestRate    number `json:"http.request.MBps"`
	ExceptionCount int64  `json:"http.exception.count"` // same as ErrorCount, for compatibility with vespa-feed-client output

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
		Operations:    stats.Operations,
		Seconds:       number(duration.Seconds()),
		SuccessCount:  stats.Successful(),
		SuccessRate:   number(float64(stats.Successful()) / math.Max(1, duration.Seconds())),
		ErrorCount:    stats.Errors,
		InflightCount: stats.Inflight,

		RequestCount:   stats.Requests,
		RequestBytes:   stats.BytesSent,
		RequestRate:    number(mbps(stats.BytesSent, duration)),
		ExceptionCount: stats.Errors,

		ResponseCount:      stats.Responses,
		ResponseBytes:      stats.BytesRecv,
		ResponseRate:       number(mbps(stats.BytesRecv, duration)),
		ResponseErrorCount: stats.Unsuccessful(),
		ResponseMinLatency: stats.MinLatency.Milliseconds(),
		ResponseAvgLatency: stats.AvgLatency().Milliseconds(),
		ResponseMaxLatency: stats.MaxLatency.Milliseconds(),
		ResponseCodeCounts: stats.ResponsesByCode,
	}
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	return enc.Encode(summary)
}
