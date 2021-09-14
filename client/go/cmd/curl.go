package cmd

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"strings"

	"github.com/kballard/go-shellquote"
	"github.com/spf13/cobra"
)

var curlDryRun bool
var curlPath string

func init() {
	rootCmd.AddCommand(curlCmd)
	curlCmd.Flags().StringVarP(&curlPath, "path", "p", "", "The path to curl. If this is unset, curl from PATH is used")
	curlCmd.Flags().BoolVarP(&curlDryRun, "dry-run", "n", false, "Print the curl command that would be executed")
}

var curlCmd = &cobra.Command{
	Use:   "curl [curl-options] path",
	Short: "Query Vespa using curl",
	Long: `Query Vespa using curl.

Execute curl with the appropriate URL, certificate and private key for your application.`,
	Example: `$ vespa curl /search/?yql=query
$ vespa curl -- -v --data-urlencode "yql=select * from sources * where title contains 'foo';" /search/
$ vespa curl -t local -- -v /search/?yql=query
`,
	DisableAutoGenTag: true,
	Args:              cobra.MinimumNArgs(1),
	Run: func(cmd *cobra.Command, args []string) {
		cfg, err := LoadConfig()
		if err != nil {
			fatalErr(err, "Could not load config")
			return
		}
		app := getApplication()
		privateKeyFile, err := cfg.PrivateKeyPath(app)
		if err != nil {
			fatalErr(err)
			return
		}
		certificateFile, err := cfg.CertificatePath(app)
		if err != nil {
			fatalErr(err)
			return
		}
		service := getService("query", 0)
		c := &curl{privateKeyPath: privateKeyFile, certificatePath: certificateFile}
		if curlDryRun {
			cmd, err := c.command(service.BaseURL, args...)
			if err != nil {
				fatalErr(err, "Failed to create curl command")
				return
			}
			log.Print(shellquote.Join(cmd.Args...))
		} else {
			if err := c.run(service.BaseURL, args...); err != nil {
				fatalErr(err, "Failed to run curl")
				return
			}
		}
	},
}

type curl struct {
	path            string
	certificatePath string
	privateKeyPath  string
}

func (c *curl) run(baseURL string, args ...string) error {
	cmd, err := c.command(baseURL, args...)
	if err != nil {
		return err
	}
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	if err := cmd.Start(); err != nil {
		return err
	}
	return cmd.Wait()
}

func (c *curl) command(baseURL string, args ...string) (*exec.Cmd, error) {
	if len(args) == 0 {
		return nil, fmt.Errorf("need at least one argument")
	}

	if c.path == "" {
		resolvedPath, err := resolveCurlPath()
		if err != nil {
			return nil, err
		}
		c.path = resolvedPath
	}

	path := args[len(args)-1]
	args = args[:len(args)-1]
	if !hasOption("--key", args) && c.privateKeyPath != "" {
		args = append(args, "--key", c.privateKeyPath)
	}
	if !hasOption("--cert", args) && c.certificatePath != "" {
		args = append(args, "--cert", c.certificatePath)
	}

	baseURL = strings.TrimSuffix(baseURL, "/")
	path = strings.TrimPrefix(path, "/")
	args = append(args, baseURL+"/"+path)

	return exec.Command(c.path, args...), nil
}

func hasOption(option string, args []string) bool {
	for _, arg := range args {
		if arg == option {
			return true
		}
	}
	return false
}

func resolveCurlPath() (string, error) {
	var curlPath string
	var err error
	curlPath, err = exec.LookPath("curl")
	if err != nil {
		curlPath, err = exec.LookPath("curl.exe")
		if err != nil {
			return "", err
		}
	}
	return curlPath, nil
}
