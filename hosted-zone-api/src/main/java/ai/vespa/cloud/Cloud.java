package ai.vespa.cloud;

/**
 * The cloud provider in which a cloud deployment may be running.
 *
 * This is "aws" when this runs in Amazon Web Services, and "gcp" when this runs in Google Cloud Platform.
 *
 * @author mpolden
 */
public record Cloud(String name) {
}
