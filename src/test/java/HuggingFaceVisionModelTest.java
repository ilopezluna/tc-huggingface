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
import java.util.List;

import static io.restassured.RestAssured.given;

public class HuggingFaceVisionModelTest {

    @Test
    public void visionModelWithHuggingFace() throws IOException {
        String imageName = "vision-model-from-hf";
        String repository = "xtuner/llava-phi-3-mini-gguf";
        String model = "llava-phi-3-mini-int4.gguf";
        String visionAdapter = "llava-phi-3-mini-mmproj-f16.gguf";
        String modelfile = """
                FROM ./llava-phi-3-mini-int4.gguf
                FROM ./llava-phi-3-mini-mmproj-f16.gguf
                TEMPLATE ""\"{{ if .System }}<|system|>
                {{ .System }}<|end|>
                {{ end }}{{ if .Prompt }}<|user|>
                {{ .Prompt }}<|end|>
                {{ end }}<|assistant|>
                {{ .Response }}<|end|>
                ""\"
                PARAMETER stop "<|user|>"
                PARAMETER stop "<|assistant|>"
                PARAMETER stop "<|system|>"
                PARAMETER stop "<|end|>"
                PARAMETER stop "<|endoftext|>"
                PARAMETER num_keep 4
                PARAMETER num_ctx 4096
                """;
        try (
                OllamaContainer ollama = new OllamaContainer(DockerImageName.parse(imageName).asCompatibleSubstituteFor("ollama/ollama:0.1.44"))
        ) {
            try {
                ollama.start();
            } catch (ContainerFetchException ex) {
                createImage(imageName, repository, model, modelfile, visionAdapter);
                ollama.start();
            }

            var image1 = getImageInBase64("car.jpeg");
            var image2 = getImageInBase64("cat.jpeg");
            var image3 = getImageInBase64("whale.jpeg");
            var image4 = getImageInBase64("computer.jpeg");

            var images = List.of(image1, image2, image3, image4);
            for (String image : images) {
                String response = given()
                        .baseUri(ollama.getEndpoint())
                        .header(new Header("Content-Type", "application/json"))
                        .body(new CompletionRequest(model + ":latest", "Describe the image in max 10 words", List.of(image), false))
                        .post("/api/generate")
                        .getBody().as(CompletionResponse.class).response();

                System.out.println("Response from LLM (🤖)-> " + response);
            }
        }
    }

    private static String getImageInBase64(String image) throws IOException {
        URL resourceUrl = OllamaVisionModelTest.class.getResource(image);
        byte[] fileContent = FileUtils.readFileToByteArray(new File(resourceUrl.getFile()));
        return Base64.getEncoder().encodeToString(fileContent);
    }

    private static void createImage(String imageName, String repository, String model, String modelfile, String visionAdapter) {
        var hfModel = new OllamaHuggingFaceContainer.HuggingFaceModel(repository, model, modelfile, visionAdapter);
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
