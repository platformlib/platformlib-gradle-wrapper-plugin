package com.platformlib.plugins.gradle.container;

import com.platformlib.plugins.gradle.container.container.PlatformLibContainerExtension;
import com.platformlib.plugins.gradle.wrapper.state.ContainerCommandState;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlatformLibGradleContainerPlugin implements Plugin<Project>  {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformLibGradleContainerPlugin.class);
    public static final String CONTAINER_COMMAND_STATE_EXT_PROPERTY_NAME = "platformlib.container.state";
    public static final String COMPOSE_CONTAINER_TASK_NAME = "containerCompose";

    @Override
    public void apply(final Project project) {
        project.getExtensions().create("platformDockerContainer", PlatformLibContainerExtension.class);
        if (!project.getRootProject().getExtensions().getExtraProperties().has(CONTAINER_COMMAND_STATE_EXT_PROPERTY_NAME)) {
            project.getRootProject().getExtensions().getExtraProperties().set(CONTAINER_COMMAND_STATE_EXT_PROPERTY_NAME, new ContainerCommandState());
        }
    }
}
