package com.platformlib.plugins.gradle.wrapper;

import com.platformlib.plugins.gradle.wrapper.configuration.DockerGradleWrapperConfiguration;
import com.platformlib.plugins.gradle.wrapper.configuration.PlatformLibGradleWrapperConfiguration;
import com.platformlib.plugins.gradle.wrapper.configuration.SshGradleWrapperConfiguration;
import com.platformlib.plugins.gradle.wrapper.utility.PlatformLibGradleWrapperUtility;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlatformLibGradleWrapperExtension extends PlatformLibGradleWrapperConfiguration {
    public static final String GRADLE_WRAPPER_PLUGIN_PROJECT_PROPERTY = "com.platformlib.gradle-wrapper";

    private final NamedDomainObjectContainer<DockerGradleWrapperConfiguration> dockerGradleWrapperConfigurations;
    private final NamedDomainObjectContainer<SshGradleWrapperConfiguration> sshGradleWrapperConfigurations;
    private List<Object> unwrappedTasks = new ArrayList<>();
    private boolean enabled;
    private File wrapperBaseDir;

    @Inject
    public PlatformLibGradleWrapperExtension(final ObjectFactory objectFactory) {
        this.dockerGradleWrapperConfigurations = objectFactory.domainObjectContainer(DockerGradleWrapperConfiguration.class);
        dockerGradleWrapperConfigurations.whenObjectAdded(wrapperConfiguration -> wrapperConfiguration.configure(PlatformLibGradleWrapperExtension.this));
        this.sshGradleWrapperConfigurations = objectFactory.domainObjectContainer(SshGradleWrapperConfiguration.class);
        sshGradleWrapperConfigurations.whenObjectAdded(wrapperConfiguration -> wrapperConfiguration.configure(PlatformLibGradleWrapperExtension.this));
    }

    public NamedDomainObjectContainer<DockerGradleWrapperConfiguration> getDocker() {
        return dockerGradleWrapperConfigurations;
    }

    public NamedDomainObjectContainer<SshGradleWrapperConfiguration> getSsh() {
        return sshGradleWrapperConfigurations;
    }

    public void setUnwrappedTasks(final List<Object> unwrappedTasks) {
        PlatformLibGradleWrapperUtility.toStringCollection(unwrappedTasks); //To validate
        this.unwrappedTasks = unwrappedTasks;
    }

    public List<Object> getUnwrappedTasks() {
        return unwrappedTasks;
    }


    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public File getWrapperBaseDir() {
        return wrapperBaseDir;
    }

    public void setWrapperBaseDir(File wrapperBaseDir) {
        this.wrapperBaseDir = wrapperBaseDir;
    }
}
