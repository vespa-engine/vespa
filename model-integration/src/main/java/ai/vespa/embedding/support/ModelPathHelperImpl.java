package ai.vespa.embedding.support;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import com.yahoo.vespa.config.UrlDownloader;
import com.yahoo.vespa.config.UrlDownloader.DownloadOptions;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public class ModelPathHelperImpl implements ModelPathHelper {

    public static final Duration MODEL_DOWNLOAD_TIMEOUT = Duration.ofMinutes(60);

    private final Secrets secrets;
    private final ModelAcquirer modelAcquirer;

    @Inject
    public ModelPathHelperImpl(Secrets secrets) {
        this.secrets = secrets;
        this.modelAcquirer = defaultModelAcquirer;
    }

    // For test purposes
    ModelPathHelperImpl(Secrets secrets, ModelAcquirer modelAcquirer) {
        this.secrets = secrets;
        this.modelAcquirer = modelAcquirer;
    }

    private ModelAcquirer defaultModelAcquirer =
         (urlReference, downloadOptions) -> {
            UrlDownloader urlDownloader = new UrlDownloader();
            File file = urlDownloader.waitFor(
                    urlReference,
                    downloadOptions,
                    MODEL_DOWNLOAD_TIMEOUT
            );

            return Paths.get(file.getAbsolutePath());
        };


    public String getModelPathResolvingIfNecessary(ModelReference modelReference) {
        if (isModelDownloadRequired(modelReference)) {
            return resolveModelAndReturnPath(modelReference);
        }

        return modelReference.toString();
    }

    private boolean isModelDownloadRequired(ModelReference modelReference) {
        return !modelReference.isResolved() &&
                modelReference.url().isPresent();
    }

    private String resolveModelAndReturnPath(ModelReference modelReference) {
        var modelUrl = modelReference.url().orElseThrow();

        var secretRef = modelReference.secretRef();
        var downloadOptions = DownloadOptions.defaultOptions();
        if (secretRef.isPresent()) {
            Secret secret = secrets.get(secretRef.get());
            downloadOptions = DownloadOptions.withAuthToken(secret.current());
        }

        return modelAcquirer.acquire(modelUrl, downloadOptions).toString();
    }

    @FunctionalInterface
    interface ModelAcquirer {
        Path acquire(UrlReference urlReference, DownloadOptions downloadOptions);
    }
}
