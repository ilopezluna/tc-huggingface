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
    public void visionModelWithHuggingFace() throws IOException {
        String repository = "vikhyatk/moondream2";
        String model = "moondream2-text-model-f16.gguf";
        String imageName = "vision-model-from-hf-1";
        String modelFile = """
                FROM %s
                PARAMETER temperature 0
                PARAMETER stop <|endoftext|>
                TEMPLATE Question: {{ .Prompt }} Image: {{ range .Images }}{{ . }}{{ end }} Answer: {{ .Response }}
                """.formatted(model);
        try (
                OllamaContainer ollama = new OllamaContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("ollama/ollama:0.1.42"))
        ) {
            try {
                ollama.start();
            } catch (ContainerFetchException ex) {
                createImage(imageName, repository, model, modelFile);
                ollama.start();
            }

            var image = getImageInBase64();

            String response = given()
                    .baseUri(ollama.getEndpoint())
                    .header(new Header("Content-Type", "application/json"))
                    .body(new CompletionRequest("moondream:latest", "Describe the image.", Collections.singletonList(image), false))
                    .post("/api/generate")
                    .getBody().as(CompletionResponse.class).response();

            System.out.println("Response from LLM (🤖)-> " + response);
        }
    }

    private static String getImageInBase64() throws IOException {
        URL resourceUrl = OllamaVisionModelTest.class.getResource("/whale.jpeg");
        byte[] fileContent = FileUtils.readFileToByteArray(new File(resourceUrl.getFile()));
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

    record CompletionRequest(String model, String prompt, List<String> images, boolean stream) {
    }

    record CompletionResponse(String response) {
    }
}
