// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package cmd

import (
	"errors"
	"fmt"
	"time"

	"github.com/fatih/color"
	"github.com/spf13/cobra"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

// Waiter waits for Vespa services to become ready, within a timeout.
type Waiter struct {
	// Timeout specifies how long we should wait for an operation to complete.
	Timeout time.Duration // TODO(mpolden): Consider making this a budget

	cli *CLI
	cmd *cobra.Command
}

// DeployService returns the service providing the deploy API on given target,
func (w *Waiter) DeployService(target vespa.Target) (*vespa.Service, error) {
	s, err := target.DeployService()
	if err != nil {
		return nil, err
	}
	if err := w.maybeWaitFor(s); err != nil {
		return nil, err
	}
	return s, nil
}

// Service returns the service identified by cluster ID, available on target.
func (w *Waiter) Service(target vespa.Target, cluster string) (*vespa.Service, error) {
	targetType, err := w.cli.targetType(anyTarget)
	if err != nil {
		return nil, err
	}
	if targetType.url != "" && cluster != "" {
		return nil, fmt.Errorf("cluster cannot be specified when target is an URL")
	}
	services, err := w.services(target)
	if err != nil {
		return nil, err
	}
	service, err := vespa.FindService(cluster, services)
	if err != nil {
		return nil, errHint(err, "The --cluster option specifies the service to use")
	}
	if err := w.maybeWaitFor(service); err != nil {
		return nil, err
	}
	return service, nil
}

// Services returns all container services available on target.
func (w *Waiter) Services(target vespa.Target) ([]*vespa.Service, error) {
	services, err := w.services(target)
	if err != nil {
		return nil, err
	}
	for _, s := range services {
		if err := w.maybeWaitFor(s); err != nil {
			return nil, err
		}
	}
	return services, nil
}

func (w *Waiter) maybeWaitFor(service *vespa.Service) error {
	if w.Timeout > 0 {
		w.cli.printInfo("Waiting up to ", color.CyanString(w.Timeout.String()), " for ", service.Description(), "...")
		return service.Wait(w.Timeout)
	}
	return nil
}

func (w *Waiter) services(target vespa.Target) ([]*vespa.Service, error) {
	if w.Timeout > 0 {
		w.cli.printInfo("Waiting up to ", color.CyanString(w.Timeout.String()), " for cluster discovery...")
	}
	return target.ContainerServices(w.Timeout)
}

// FastWaitOn returns whether we should use a short default timeout for given target.
func (w *Waiter) FastWaitOn(target vespa.Target) bool {
	return target.IsCloud() && w.Timeout == 0 && !w.cmd.PersistentFlags().Changed("wait")
}

// Deployment waits for a deployment to become ready, returning the ID of the converged deployment.
func (w *Waiter) Deployment(target vespa.Target, wantedID int64) (int64, error) {
	timeout := w.Timeout
	fastWait := w.FastWaitOn(target)
	if timeout > 0 {
		w.cli.printInfo("Waiting up to ", color.CyanString(timeout.String()), " for deployment to converge...")
	} else if fastWait {
		// If --wait is not explicitly given, we always wait a few seconds in Cloud to catch fast failures, e.g.
		// invalid application package
		timeout = 2 * time.Second
	}
	id, err := target.AwaitDeployment(wantedID, timeout)
	if errors.Is(err, vespa.ErrWaitTimeout) {
		if fastWait {
			return id, nil // Do not report fast wait timeout as an error
		}
		if target.IsCloud() {
			w.cli.printInfo("Timed out waiting for deployment to converge. See ", color.CyanString(target.Deployment().System.ConsoleRunURL(target.Deployment(), wantedID)), " for more details")
		}
	}
	return id, err
}
