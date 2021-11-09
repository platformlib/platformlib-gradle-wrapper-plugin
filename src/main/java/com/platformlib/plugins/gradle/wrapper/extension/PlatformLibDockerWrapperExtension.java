package com.platformlib.plugins.gradle.wrapper.extension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//Use classic POJO because of gradle 4.X compatibility
public class PlatformLibDockerWrapperExtension {
    private String image;
    private String workDir;
    private List<String> dockerOptions = new ArrayList<>();
    private List<String> bindMounts = new ArrayList<>();
    private Map<String, String> env = new HashMap<>();
    private List<String> commandAndArguments = new ArrayList<>();

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    public List<String> getDockerOptions() {
        return dockerOptions;
    }

    public void setDockerOptions(List<String> dockerOptions) {
        this.dockerOptions = dockerOptions;
    }

    public List<String> getBindMounts() {
        return bindMounts;
    }

    public void setBindMounts(List<String> bindMounts) {
        this.bindMounts = bindMounts;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public List<String> getCommandAndArguments() {
        return commandAndArguments;
    }

    public void setCommandAndArguments(List<String> commandAndArguments) {
        this.commandAndArguments = commandAndArguments;
    }
}
