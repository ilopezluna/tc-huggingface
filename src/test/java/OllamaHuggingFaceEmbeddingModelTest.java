import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.huggingface.OllamaHuggingFaceContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class OllamaHuggingFaceEmbeddingModelTest {

    @Test
    public void embeddingModelWithHuggingFace() {
        String repository = "CompendiumLabs/bge-small-en-v1.5-gguf";
        String model = "bge-small-en-v1.5-q4_k_m.gguf";
        String imageName = "embedding-model-from-hf";
        try (
            OllamaContainer ollama = new OllamaContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("ollama/ollama:0.1.42"))
        ) {
            try {
                ollama.start();
            } catch (ContainerFetchException ex) {
                // Create the image
                createImage(imageName, repository, model);
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

    private static void createImage(String imageName, String repository, String model) {
        var hfModel = new OllamaHuggingFaceContainer.HuggingFaceModel(repository, model);
        try (var huggingFaceContainer = new OllamaHuggingFaceContainer(hfModel)) {
            huggingFaceContainer.start();
            huggingFaceContainer.commitToImage(imageName);
            huggingFaceContainer.stop();
        }
    }

    public record EmbeddingRequest(String model, String prompt) {}
}
