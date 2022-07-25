// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// vespa logfmt command
// Author: arnej

package logfmt

import (
	"bufio"
	"fmt"
	"os"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"golang.org/x/sys/unix"
)

type myOptions struct {
	showFlags    flagValueForShow
	levelFlags   flagValueForLevel
	onlyhst      string
	onlypid      string
	onlysvc      string
	onlyint      bool
	compore      string
	msgtxre      string
	optfollow    bool
	optnldequote bool
	shortsvc     bool
	shortcmp     bool
	compFilter   *regexp.Regexp
	msgFilter    *regexp.Regexp
}

func (o *myOptions) showField(field string) bool {
	return o.showFlags.shown[field]
}

func (o *myOptions) showLevel(level string) bool {
	rv, ok := o.levelFlags.levels[level]
	if !ok {
		o.levelFlags.levels[level] = true
		fmt.Fprintf(os.Stderr, "Warnings: unknown level '%s' in input\n", level)
		return true
	}
	return rv
}

func NewLogfmtCommand() *cobra.Command {
	var (
		curOptions myOptions
	)
	cmd := &cobra.Command{
		Use:   "logfmt",
		Short: "convert vespa.log to human-readable format",
		Long: `vespa logfmt takes input in the internal vespa format
and converts it to something human-readable`,
		Run: func(cmd *cobra.Command, args []string) {
			runLogfmt(&curOptions, args)
		},
		Args: cobra.MaximumNArgs(1),
	}
	curOptions.levelFlags.levels = defaultLevelFlags()
	curOptions.showFlags.shown = defaultShowFlags()
	cmd.Flags().VarP(&curOptions.levelFlags, "level", "l", "turn levels on/off\n")
	cmd.Flags().VarP(&curOptions.showFlags, "show", "s", "turn fields shown on/off\n")
	cmd.Flags().BoolVar(&curOptions.onlyint, "internal", false, "select only internal components")
	cmd.Flags().BoolVar(&curOptions.shortsvc, "truncateservice", false, "truncate service name")
	cmd.Flags().BoolVarP(&curOptions.optfollow, "follow", "f", false, "follow logfile with tail -f")
	cmd.Flags().BoolVarP(&curOptions.optnldequote, "nldequote", "N", false, "dequote newlines embedded in message")
	cmd.Flags().BoolVarP(&curOptions.shortcmp, "truncatecomponent", "X", false, "truncate component name")
	cmd.Flags().StringVar(&curOptions.compore, "component", "", "select components by regexp")
	cmd.Flags().StringVar(&curOptions.msgtxre, "message", "", "select messages by regexp")
	cmd.Flags().StringVarP(&curOptions.onlyhst, "host", "H", "", "select only one host")
	cmd.Flags().StringVarP(&curOptions.onlypid, "pid", "p", "", "select only one process ID")
	cmd.Flags().StringVarP(&curOptions.onlysvc, "service", "S", "", "select only one service")
	return cmd
}

func inputIsTty() bool {
	_, err := unix.IoctlGetWinsize(int(os.Stdin.Fd()), unix.TIOCGWINSZ)
	return err == nil
}

func vespaHome() string {
	ev := os.Getenv("VESPA_HOME")
	if ev == "" {
		return "/opt/vespa"
	}
	return ev
}

func runLogfmt(opts *myOptions, args []string) {
	if opts.compore != "" {
		re, err := regexp.Compile(opts.compore)
		if err != nil {
			fmt.Fprintf(os.Stderr, "bad component-match regexp '%s': %v", opts.compore, err)
		} else {
			opts.compFilter = re
		}
	}
	if opts.msgtxre != "" {
		re, err := regexp.Compile(opts.msgtxre)
		if err != nil {
			fmt.Fprintf(os.Stderr, "bad message-match regexp '%s': %v", opts.msgtxre, err)
		} else {
			opts.msgFilter = re
		}
	}
	if len(args) == 0 {
		if inputIsTty() {
			args = append(args, vespaHome()+"/logs/vespa/vespa.log")
		} else {
			formatFile(opts, os.Stdin)
		}
	}
	for _, arg := range args {
		file, err := os.Open(arg)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Cannot open '%s': %v", err)
		} else {
			formatFile(opts, file)
		}
	}
}

func formatFile(opts *myOptions, arg *os.File) {
	stdout := os.Stdout
	input := bufio.NewScanner(arg)
	input.Buffer(make([]byte, 64*1024), 4*1024*1024)
	for input.Scan() {
		output, err := handle(opts, input.Text())
		if err != nil {
			fmt.Fprintln(os.Stdout, "bad log line:", err)
		} else {
			stdout.WriteString(output)
		}
	}
}

func handle(opts *myOptions, line string) (output string, err error) {
	fields := strings.Split(line, "\t")
	if len(fields) > 6 {
		timestampfield := fields[0] // seconds, optional fractional seconds
		hostfield := fields[1]
		pidfield := fields[2] // pid, optional tid
		servicefield := fields[3]
		componentfield := fields[4]
		levelfield := fields[5]
		messagefields := fields[6:]

		if !opts.showLevel(levelfield) {
			return "", nil
		}
		if opts.onlyhst != "" && opts.onlyhst != hostfield {
			return "", nil
		}
		if opts.onlypid != "" && opts.onlypid != pidfield {
			return "", nil
		}
		if opts.onlysvc != "" && opts.onlysvc != servicefield {
			return "", nil
		}
		if opts.onlyint && !isInternal(componentfield) {
			return "", nil
		}
		if opts.compFilter != nil && opts.compFilter.FindStringIndex(componentfield) == nil {
			return "", nil
		}
		if opts.msgFilter != nil {
			msgs := strings.Join(messagefields, "\t")
			if opts.msgFilter.FindStringIndex(msgs) == nil {
				return "", nil
			}
		}

		var buf strings.Builder

		if opts.showField("fmttime") {
			secs, err := strconv.ParseFloat(timestampfield, 64)
			if err != nil {
				return "", err
			}
			nsecs := int64(secs * 1e9)
			timestamp := time.Unix(0, nsecs)
			if opts.showField("usecs") {
				buf.WriteString(timestamp.Format("[2006-01-02 15:04:05.000000] "))
			} else if opts.showField("msecs") {
				buf.WriteString(timestamp.Format("[2006-01-02 15:04:05.000] "))
			} else {
				buf.WriteString(timestamp.Format("[2006-01-02 15:04:05] "))
			}
		} else if opts.showField("time") {
			buf.WriteString(timestampfield)
			buf.WriteString(" ")
		}
		if opts.showField("host") {
			buf.WriteString(fmt.Sprintf("%-8s ", hostfield))
		}
		if opts.showField("level") {
			buf.WriteString(fmt.Sprintf("%-7s ", strings.ToUpper(levelfield)))
		}
		if opts.showField("pid") {
			// onlypid, _, _ := strings.Cut(pidfield, "/")
			buf.WriteString(fmt.Sprintf("%6s ", pidfield))
		}
		if opts.showField("service") {
			if opts.shortsvc {
				buf.WriteString(fmt.Sprintf("%-9.9s ", servicefield))
			} else {
				buf.WriteString(fmt.Sprintf("%-16s ", servicefield))
			}
		}
		if opts.showField("component") {
			if opts.shortcmp {
				buf.WriteString(fmt.Sprintf("%-15.15s ", componentfield))
			} else {
				buf.WriteString(fmt.Sprintf("%s\t", componentfield))
			}
		}
		if opts.showField("message") {
			var msgBuf strings.Builder
			for idx, message := range messagefields {
				if idx > 0 {
					msgBuf.WriteString("\n\t")
				}
				if opts.optnldequote {
					message = strings.ReplaceAll(message, "\\n\\t", "\n\t")
					message = strings.ReplaceAll(message, "\\n", "\n\t")
				}
				msgBuf.WriteString(message)
			}
			message := msgBuf.String()
			if strings.Contains(message, "\n") {
				buf.WriteString("\n\t")
			}
			buf.WriteString(message)
		}
		buf.WriteString("\n")
		output = buf.String()
	}
	return
}
