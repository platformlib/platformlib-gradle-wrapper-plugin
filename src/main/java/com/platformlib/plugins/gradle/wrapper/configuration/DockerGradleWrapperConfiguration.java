package com.platformlib.plugins.gradle.wrapper.configuration;

import java.util.ArrayList;
import java.util.Collection;

public class DockerGradleWrapperConfiguration extends PlatformLibGradleWrapperConfiguration {
    private final String name;
    private String image;
    private boolean useCurrentJava;
    private Collection<String> m2Artifacts = new ArrayList<>();
    private Collection<String> dockerOptions = new ArrayList<>();
    private String workDir;
    private boolean mapBuildDir;
    private boolean bindLocalM2Repository;
    private Boolean mapProjectDir;
    private Collection<String> additionalGradleArguments = new ArrayList<>();
    private Boolean usePodman;

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

    public void setBindLocalM2Repository(boolean bindLocalM2Repository) {
        this.bindLocalM2Repository = bindLocalM2Repository;
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


    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public boolean isMapBuildDir() {
        return mapBuildDir;
    }

    public void mapBuildDir() {
        this.mapBuildDir = true;
    }

    public void bindLocalM2Repository() {
        this.bindLocalM2Repository = true;
    }

    public boolean isBindLocalM2Repository() {
        return bindLocalM2Repository;
    }

    public Boolean getMapProjectDir() {
        return mapProjectDir;
    }

    public void mapProjectDir() {
        setMapProjectDir(true);
    }

    public void setMapProjectDir(Boolean mapProjectDir) {
        this.mapProjectDir = mapProjectDir;
    }

    public Collection<String> getAdditionalGradleArguments() {
        return additionalGradleArguments;
    }

    public void setAdditionalGradleArguments(Collection<String> additionalGradleArguments) {
        this.additionalGradleArguments = additionalGradleArguments;
    }

    public Boolean getUsePodman() {
        return usePodman;
    }

    public void setUsePodman(Boolean usePodman) {
        this.usePodman = usePodman;
    }
}
