package cmd

import (
	"fmt"
	"time"

	"github.com/fatih/color"
	"github.com/vespa-engine/vespa/client/go/internal/vespa"
)

// Waiter waits for Vespa services to become ready, within a timeout.
type Waiter struct {
	// Once species whether we should wait at least one time, irregardless of timeout.
	Once bool

	// Timeout specifies how long we should wait for an operation to complete.
	Timeout time.Duration // TODO(mpolden): Consider making this a budget

	cli *CLI
}

func (w *Waiter) wait() bool { return w.Once || w.Timeout > 0 }

// DeployService returns the service providing the deploy API on given target,
func (w *Waiter) DeployService(target vespa.Target) (*vespa.Service, error) {
	s, err := target.DeployService()
	if err != nil {
		return nil, err
	}
	if w.Timeout > 0 {
		w.cli.printInfo("Waiting up to ", color.CyanString(w.Timeout.String()), " for ", s.Description(), " to become ready...")
	}
	if w.wait() {
		if err := s.Wait(w.Timeout); err != nil {
			return nil, err
		}
	}
	return s, nil
}

// Service returns the service identified by cluster ID, available on target.
func (w *Waiter) Service(target vespa.Target, cluster string) (*vespa.Service, error) {
	targetType, err := w.cli.targetType()
	if err != nil {
		return nil, err
	}
	if targetType.url != "" && cluster != "" {
		return nil, fmt.Errorf("cluster cannot be specified when target is an URL")
	}
	services, err := w.Services(target)
	if err != nil {
		return nil, err
	}
	service, err := vespa.FindService(cluster, services)
	if err != nil {
		return nil, errHint(err, "The --cluster option specifies the service to use")
	}
	if w.Timeout > 0 {
		w.cli.printInfo("Waiting up to ", color.CyanString(w.Timeout.String()), " for ", color.CyanString(service.Description()), " to become available...")
	}
	if w.wait() {
		if err := service.Wait(w.Timeout); err != nil {
			return nil, err
		}
	}
	return service, nil
}

// Services returns all container services available on target.
func (w *Waiter) Services(target vespa.Target) ([]*vespa.Service, error) {
	if w.Timeout > 0 {
		w.cli.printInfo("Waiting up to ", color.CyanString(w.Timeout.String()), " for cluster discovery...")
	}
	services, err := target.ContainerServices(w.Timeout)
	if err != nil {
		return nil, err
	}
	for _, s := range services {
		if w.Timeout > 0 {
			w.cli.printInfo("Waiting up to ", color.CyanString(w.Timeout.String()), " for ", s.Description(), "...")
		}
		if w.wait() {
			if err := s.Wait(w.Timeout); err != nil {
				return nil, err
			}
		}
	}
	return services, nil
}

// Deployment waits for a deployment to become ready, returning the ID of the converged deployment.
func (w *Waiter) Deployment(target vespa.Target, id int64) (int64, error) {
	if w.Timeout > 0 {
		w.cli.printInfo("Waiting up to ", color.CyanString(w.Timeout.String()), " for deployment to converge...")
	}
	return target.AwaitDeployment(id, w.Timeout)
}
