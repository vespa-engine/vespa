package cmd

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/toolkit"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"time"
)

func newToolkitCmd(cli *CLI) *cobra.Command {
	return &cobra.Command{
		Use:   "toolkit",
		Short: "Starts the Vespa toolkit servers",
		Long: `The 'vespa toolkit' command initializes and starts both the frontend and proxy servers. 
The frontend server serves the Vespa toolkit's user interface, while the proxy server acts 
as a reverse proxy to the Vespa service, especially for cloud instances. This toolkit provides 
a convenient interface for interacting with the Vespa service.`,
		DisableAutoGenTag: true,
		SilenceUsage:      true,
		RunE: func(cmd *cobra.Command, args []string) error {
			fmt.Println("This command will start the Vespa toolkit which includes frontend and proxy servers.")
			fmt.Print("Do you want to continue? (Y/n): ")
			reader := bufio.NewReader(os.Stdin)
			response, err := reader.ReadString('\n')
			if err != nil {
				return fmt.Errorf("failed to read user input: %w", err)
			}
			response = strings.ToLower(strings.TrimSpace(response))

			// Treat empty response as "yes" since "Y" is the default
			if response == "" || response == "y" || response == "yes" {
				target, err := cli.target(targetOptions{})
				if err != nil {
					return err
				}
				service, err := target.Service(vespa.QueryService, time.Duration(0)*time.Second, 0, cli.config.cluster())
				if err != nil {
					return err
				}

				var wg sync.WaitGroup

				// Initialize and start the FrontendServer
				var frontendServer *toolkit.FrontendServer
				frontendServer = toolkit.NewFrontendServer()
				wg.Add(1)
				go func() {
					defer wg.Done()
					fmt.Printf("Frontend server is running on http://localhost:%d\n", frontendServer.Port)
					if err := frontendServer.Start(); err != nil && !errors.Is(err, http.ErrServerClosed) {
						log.Printf("Frontend server stopped with error: %v\n", err)
					}
				}()

				// Only initialize and start the ProxyServer if target is cloud
				var proxyServer *toolkit.ProxyServer
				if target.IsCloud() {
					proxyServer, err = toolkit.NewProxyServer(service)
					if err != nil {
						return fmt.Errorf("could not initialize proxy server: %w", err)
					}
					wg.Add(1)
					go func() {
						defer wg.Done()
						fmt.Printf("Proxy server is running on http://localhost:%d\n", proxyServer.Port)
						if err := proxyServer.Start(); err != nil && !errors.Is(err, http.ErrServerClosed) {
							log.Printf("Proxy server stopped with error: %v\n", err)
						}
					}()
				}

				// Signal listener for graceful shutdown
				c := make(chan os.Signal, 1)
				signal.Notify(c, os.Interrupt)
				go func() {
					<-c
					ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
					defer cancel()
					// Stop frontend server
					if err := frontendServer.Shutdown(ctx); err != nil {
						log.Printf("Error stopping frontend server: %v\n", err)
					}
					// Stop proxy server if it was started
					if proxyServer != nil {
						if err := proxyServer.Shutdown(ctx); err != nil {
							log.Printf("Error stopping proxy server: %v\n", err)
						}
					}
					// Decrement the wait group for each server to allow the main goroutine to exit
					wg.Done()
					if proxyServer != nil {
						wg.Done()
					}
				}()

				wg.Wait()
				return nil
			} else {
				return nil // Exit if the user chooses "no"
			}
		},
	}
}
