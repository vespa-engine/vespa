package ai.vespa.embedding;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.yahoo.config.ModelReference;
import com.yahoo.config.UrlReference;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelPathHelperImplTest {
    public static final String PRIVATE_MODEL_URL = "https://model.url/private";
    public static final String PUBLIC_MODEL_URL = "https://model.url/public";

    private static String SECRET_REF = "secret";
    private static String SECRET_VALUE = "token value";

    ModelPathHelper modelPathHelper = new ModelPathHelperImpl(
            new MockSecrets(SECRET_VALUE),
            (urlReference, downloadOptions) -> {
                if(downloadOptions.authToken().equals(Optional.of(SECRET_VALUE))) {
                    return Path.of("downloaded/private/model/path");
                }
                return Path.of("downloaded/public/model/path");
            }
    );

    @Test
    void return_resolved_model_path_if_model_is_resolved() {
        Path actualPath = modelPathHelper.getModelPathResolvingIfNecessary(ModelReference.resolved(Path.of("resolved/model/path")));

        assertEquals("resolved/model/path", actualPath.toString());
    }

    @Test
    void download_and_return_public_model() {
        ModelReference unresolved = ModelReference.unresolved(
                Optional.empty(),
                Optional.of(UrlReference.valueOf(PUBLIC_MODEL_URL)),
                Optional.empty(),
                Optional.empty());

        Path actualPath = modelPathHelper.getModelPathResolvingIfNecessary(unresolved);

        assertEquals("downloaded/public/model/path", actualPath.toString());
    }

    @Test
    void download_and_return_private_model() {
        ModelReference unresolved = ModelReference.unresolved(
                Optional.empty(),
                Optional.of(UrlReference.valueOf(PRIVATE_MODEL_URL)),
                Optional.of(SECRET_REF),
                Optional.empty());

        Path actualPath = modelPathHelper.getModelPathResolvingIfNecessary(unresolved);

        assertEquals("downloaded/private/model/path", actualPath.toString());
    }

    static class MockSecrets implements Secrets {
        private final String secretValue;

        // Constructor that allows specifying a custom API key
        MockSecrets(String secretValue) {
            this.secretValue = secretValue;
        }

        @Override
        public Secret get(String key) {
            if (key.equals(SECRET_REF)) {
                return () -> secretValue;
            }
            return null;
        }
    }
}
