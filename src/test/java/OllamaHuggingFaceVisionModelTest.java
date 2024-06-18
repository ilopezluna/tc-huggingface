import io.restassured.http.Header;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.huggingface.OllamaHuggingFaceContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class OllamaHuggingFaceVisionModelTest {

    @Test
    public void visionModelWithHuggingFace() {
        String repository = "vikhyatk/moondream2";
        String model = "moondream2-text-model-f16.gguf";
        String imageName = "vision-model-from-hf";
        String modelFile = """
                FROM moondream2-text-model-f16.gguf
                PARAMETER temperature 0
                PARAMETER stop <|endoftext|>
                TEMPLATE ""\"{{ if .Prompt }} Question: {{ .Prompt }}
                
                         {{ end }} Answer: {{ .Response }}
                         ""\"
                """;
        try (
                OllamaContainer ollama = new OllamaContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("ollama/ollama:0.1.42"))
        ) {
            try {
                ollama.start();
            } catch (ContainerFetchException ex) {
                // Create the image
                createImage(imageName, repository, model, modelFile);
                ollama.start();
            }

            String modelName = given()
                    .baseUri(ollama.getEndpoint())
                    .get("/api/tags")
                    .jsonPath()
                    .getString("models[0].name");
            assertThat(modelName).contains(model + ":latest");

            var image = getImageInBase64();

            String response = given()
                    .baseUri(ollama.getEndpoint())
                    .header(new Header("Content-Type", "application/json"))
                    .body(new CompletionRequest(model + ":latest", "which colors has the image?", Collections.singletonList(image), false))
                    .post("/api/generate")
                    .getBody().asString();

            System.out.println(response);
        }
    }

    private static String getImageInBase64() {
        URL resourceUrl = OllamaHuggingFaceVisionModelTest.class.getResource("/whale.jpeg");
        byte[] fileContent = null;
        try {
            fileContent = FileUtils.readFileToByteArray(new File(resourceUrl.getFile()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Base64.getEncoder().encodeToString(fileContent);
    }

    private static void createImage(String imageName, String repository, String model, String modelFile) {
        var hfModel = new OllamaHuggingFaceContainer.HuggingFaceModel(repository, model, modelFile);
        try (var huggingFaceContainer = new OllamaHuggingFaceContainer(hfModel)) {
            huggingFaceContainer.start();
            huggingFaceContainer.commitToImage(imageName);
            huggingFaceContainer.stop();
        }
    }

    record CompletionRequest(String model, String prompt, List<String> images, boolean stream) {}
}
