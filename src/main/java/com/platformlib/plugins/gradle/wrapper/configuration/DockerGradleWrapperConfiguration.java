package com.platformlib.plugins.gradle.wrapper.configuration;

import java.util.ArrayList;
import java.util.Collection;

public class DockerGradleWrapperConfiguration extends PlatformLibGradleWrapperConfiguration {
    private final String name;
    private String image;
    private boolean useCurrentJava;
    private Collection<String> m2Artifacts = new ArrayList<>();
    private Collection<String> dockerOptions = new ArrayList<>();

    public DockerGradleWrapperConfiguration(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(final String image) {
        this.image = image;
    }

    public boolean isUseCurrentJava() {
        return useCurrentJava;
    }

    public void setUseCurrentJava(boolean useCurrentJava) {
        this.useCurrentJava = useCurrentJava;
    }

    public Collection<String> getM2Artifacts() {
        return m2Artifacts;
    }

    public void setM2Artifacts(Collection<String> m2Artifacts) {
        this.m2Artifacts = m2Artifacts;
    }

    public Collection<String> getDockerOptions() {
        return dockerOptions;
    }

    public void setDockerOptions(Collection<String> dockerOptions) {
        this.dockerOptions = dockerOptions;
    }
}
