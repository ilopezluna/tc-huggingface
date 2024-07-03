package org.testcontainers.huggingface;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

public class OllamaHuggingFaceContainer extends OllamaContainer {

    private final HuggingFaceModel huggingFaceModel;

    public OllamaHuggingFaceContainer(HuggingFaceModel model) {
        super(DockerImageName.parse("ollama/ollama:0.1.47"));
        this.huggingFaceModel = model;
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo, boolean reused) {
        super.containerIsStarted(containerInfo, reused);
        if (reused || huggingFaceModel == null) {
            return;
        }

        try {
            executeCommand("apt-get", "update");
            executeCommand("apt-get", "upgrade", "-y");
            executeCommand("apt-get", "install", "-y", "python3-pip");
            executeCommand("pip", "install", "huggingface-hub");
            executeCommand(
                    "huggingface-cli",
                    "download",
                    huggingFaceModel.repository,
                    huggingFaceModel.model,
                    "--local-dir",
                    "."
            );
            if (huggingFaceModel.visionAdapter != null) {
                executeCommand(
                        "huggingface-cli",
                        "download",
                        huggingFaceModel.repository,
                        huggingFaceModel.visionAdapter,
                        "--local-dir",
                        "."
                );
            }
            executeCommand("sh", "-c", String.format("echo '%s' > Modelfile", huggingFaceModel.modelfileContent));
            executeCommand("ollama", "create", huggingFaceModel.model, "-f", "Modelfile");
            executeCommand("rm", huggingFaceModel.model);
        } catch (IOException | InterruptedException e) {
            throw new ContainerLaunchException(e.getMessage());
        }
    }

    private void executeCommand(String... command) throws ContainerLaunchException, IOException, InterruptedException {
        ExecResult execResult = execInContainer(command);
        if (execResult.getExitCode() > 0) {
            throw new ContainerLaunchException(
                    "Failed to execute " + String.join(" ", command) + ": " + execResult.getStderr()
            );
        }
    }

    public record HuggingFaceModel(String repository, String model, String modelfileContent, String visionAdapter) {
        public HuggingFaceModel(String repository, String model) {
            this(repository, model, "FROM " + model, null);
        }
    }
}
