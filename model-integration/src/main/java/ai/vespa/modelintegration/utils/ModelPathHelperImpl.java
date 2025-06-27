// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.modelintegration.utils;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import com.yahoo.vespa.config.UrlDownloader;
import com.yahoo.vespa.config.UrlDownloader.DownloadOptions;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Helper component responsible for resolving {@link ModelReference} instances to local file system paths.
 * <p>
 * If the model reference is not already resolved (i.e., it does not point to a local file), this class
 * initiates a download request via the config-proxy using the remote URL specified in the reference,
 * and returns the path to the downloaded file.
 * <p>
 * If the model is already resolved, it simply returns the local path without performing any download.
 * <p>
 * The actual download is performed by the config-proxy, which retrieves the model and stores it on the file system of the host.
 * The returned path points to the downloaded file in the host's local file system.
 * The RPC timeout for this operation is controlled by `MODEL_DOWNLOAD_TIMEOUT` (60 minutes), and matches
 * the timeout used during the config deserialization phase when acquiring model configuration.
 * <p>
 * Downloading supports optional bearer token authentication. The token is retrieved from a secret
 * referenced by the {@code secretRef} attribute in the {@link ModelReference}, using the injected {@link Secrets} store.
 * Currently, only bearer token-based authentication is supported.
 *
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * Path path = modelPathHelper.getModelPathResolvingIfNecessary(modelReference);
 * }</pre>
 *
 * <p>This class is typically managed by the Vespa component model and constructed via dependency injection.</p>
 *
 * @author Onur
 * @see ModelReference
 * @see Secrets
 * @see UrlDownloader
 */
public class ModelPathHelperImpl extends AbstractComponent implements ModelPathHelper {

    public static final Duration MODEL_DOWNLOAD_TIMEOUT = Duration.ofMinutes(60);

    private final Secrets secrets;
    private final ModelResolverFunction modelResolverFunction;

    private UrlDownloader urlDownloader;

    @Inject
    public ModelPathHelperImpl(Secrets secrets) {
        this.secrets = secrets;
        this.urlDownloader = new UrlDownloader();
        this.modelResolverFunction = defaultModelResolverFunction;
    }

    // For test purposes
    ModelPathHelperImpl(Secrets secrets, ModelResolverFunction modelResolverFunction) {
        this.secrets = secrets;
        this.modelResolverFunction = modelResolverFunction;
    }

    private ModelResolverFunction defaultModelResolverFunction =
         (urlReference, downloadOptions) -> {
            File file = urlDownloader.waitFor(
                    urlReference,
                    downloadOptions,
                    MODEL_DOWNLOAD_TIMEOUT
            );

            return Paths.get(file.getAbsolutePath());
        };

    @Override
    public void deconstruct() {
        urlDownloader.shutdown();
        super.deconstruct();
    }

    @Override
    public Path getModelPathResolvingIfNecessary(ModelReference modelReference) {
        if (isModelDownloadRequired(modelReference)) {
            return resolveModelAndReturnPath(modelReference);
        }

        return modelReference.value();
    }

    private boolean isModelDownloadRequired(ModelReference modelReference) {
        return !modelReference.isResolved() &&
                modelReference.url().isPresent();
    }

    private Path resolveModelAndReturnPath(ModelReference modelReference) {
        var modelUrl = modelReference.url().orElseThrow();

        var secretRef = modelReference.secretRef();
        var downloadOptions = DownloadOptions.defaultOptions();
        if (secretRef.isPresent()) {
            Secret secret = secrets.get(secretRef.get());
            downloadOptions = DownloadOptions.ofAuthToken(secret.current());
        }

        return modelResolverFunction.apply(modelUrl, downloadOptions);
    }

    @FunctionalInterface
    interface ModelResolverFunction {
        Path apply(UrlReference urlReference, DownloadOptions downloadOptions);
    }
}
