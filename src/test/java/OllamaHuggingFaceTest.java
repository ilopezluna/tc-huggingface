import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.huggingface.OllamaHuggingFaceContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class OllamaHuggingFaceTest {

    @Test
    public void embeddingModelWithHuggingFace() {
        String repository = "CompendiumLabs/bge-small-en-v1.5-gguf";
        String model = "bge-small-en-v1.5-q4_k_m.gguf";
        String imageName = "embedding-model-from-hf";
        try (
            OllamaContainer ollama = new OllamaContainer(
                DockerImageName.parse(imageName).asCompatibleSubstituteFor("ollama/ollama:0.1.42")
            )
        ) {
            try {
                ollama.start();
            } catch (ContainerFetchException ex) {
                // Create the image
                try (
                    OllamaHuggingFaceContainer huggingFaceContainer = new OllamaHuggingFaceContainer(
                        imageName,
                        new OllamaHuggingFaceContainer.HuggingFaceModel(repository, model)
                    )
                ) {
                    huggingFaceContainer.start();
                    huggingFaceContainer.stop();
                }
                ollama.start();
            }

            String modelName = given()
                .baseUri(ollama.getEndpoint())
                .get("/api/tags")
                .jsonPath()
                .getString("models[0].name");
            assertThat(modelName).contains(model + ":latest");

            List<Float> embedding = given()
                .baseUri(ollama.getEndpoint())
                .header(new Header("Content-Type", "application/json"))
                .body(new EmbeddingRequest(model + ":latest", "Hello from Testcontainers!"))
                .post("/api/embeddings")
                .jsonPath()
                .getList("embedding");

            assertThat(embedding).isNotNull();
            assertThat(embedding.isEmpty()).isFalse();
        }
    }

    public static class EmbeddingRequest {

        public final String model;

        public final String prompt;

        public EmbeddingRequest(String model, String prompt) {
            this.model = model;
            this.prompt = prompt;
        }
    }
}
