package org.testcontainers.huggingface;

import org.jetbrains.annotations.NotNull;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.MountableFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HuggingFaceContainerTest2 {
    public static void main(String[] args) throws IOException, InterruptedException {
        GenericContainer<OllamaContainer> withHuggingFaceCLI = new GenericContainer<OllamaContainer>(
                new ImageFromDockerfile()
                        .withDockerfileFromBuilder(builder ->
                                builder
                                        .from("ollama/ollama:0.1.26")
                                        .run("apt-get update && apt-get upgrade -y && apt-get install -y python3-pip")
                                        .run("pip install huggingface-hub")
                                        .build()))
                .withExposedPorts(11434);

        withHuggingFaceCLI.start();
        Container.ExecResult downloadModelFromHF = withHuggingFaceCLI.execInContainer("huggingface-cli", "download",
                "CompendiumLabs/bge-small-en-v1.5-gguf",
                "bge-small-en-v1.5-q4_k_m.gguf",
                "--local-dir", "/downloads");

        Container.ExecResult createModelFile = withHuggingFaceCLI.execInContainer("touch", "Modelfile");
        Container.ExecResult fillModelFile = withHuggingFaceCLI.execInContainer("sh", "-c", "echo 'FROM downloads/bge-small-en-v1.5-q4_k_m.gguf' > Modelfile");

        Container.ExecResult buildModel = withHuggingFaceCLI.execInContainer("ollama", "create", "example", "-f", "Modelfile");

        try {
            String s = "http://" + withHuggingFaceCLI.getHost() + ":" + withHuggingFaceCLI.getMappedPort(11434);
            HttpURLConnection connection = getHttpURLConnection(s);
            int responseCode = connection.getResponseCode();
            StringBuilder content = getResponseBody(connection);
            System.out.println("Response Code: " + responseCode);
            System.out.println("Response: " + content);
            connection.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }

        withHuggingFaceCLI.stop();
    }

    private static @NotNull StringBuilder getResponseBody(HttpURLConnection connection) throws IOException {
        InputStream responseStream = connection.getInputStream();
        BufferedReader in = new BufferedReader(new InputStreamReader(responseStream));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        return content;
    }

    private static @NotNull HttpURLConnection getHttpURLConnection(String endpoint) throws IOException {
        URL url = new URL(endpoint + "/api/embeddings");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        String jsonInputString = "{ \"model\": \"example:latest\", \"prompt\": \"Llamas are members of the camelid family\" }";
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return connection;
    }
}