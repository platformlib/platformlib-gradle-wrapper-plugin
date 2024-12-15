package com.platformlib.plugins.gradle.container.utility;

import com.platformlib.os.api.OsPlatform;
import com.platformlib.os.api.enums.OsFamily;
import com.platformlib.os.local.LocalOsPlatform;
import com.platformlib.plugins.gradle.wrapper.state.ContainerCommandState;
import com.platformlib.process.api.ProcessInstance;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class PlatformLibContainerUtility {
    private final Lock detectCommandLock = new ReentrantLock();
    private final ContainerCommandState containerCommandState = new ContainerCommandState();

    private static final PlatformLibContainerUtility INSTANCE = new PlatformLibContainerUtility();


    private PlatformLibContainerUtility() {
    }

    private static String getCheckedStateContainerCommand(final ContainerCommandState state) {
        if (!state.getChecked()) {
            throw new IllegalStateException("The method must be called only for checked state");
        }
        if (state.getException() != null) {
            throw new IllegalStateException("The container command cannot be detected", state.getException());
        }
        return state.getCommand();
    }

    private String detectContainerCommandInternal(final ContainerCommandState state) {
        if (state.getChecked()) {
            return getCheckedStateContainerCommand(state);
        }
        try {
            detectCommandLock.lockInterruptibly();
            if (state.getChecked()) {
                return getCheckedStateContainerCommand(state);
            }
            try (OsPlatform osPlatform = LocalOsPlatform.getInstance()) {
                if (osPlatform.getOsSystem().getOsFamily() != OsFamily.UNIX) {
                    throw new IllegalStateException("Only NIX based OS are supported");
                }
                for (String containerCommand : Arrays.asList("podman", "docker")) {
                    final ProcessInstance processInstance = osPlatform
                            .newProcessBuilder()
                            .commandAndArguments("which", containerCommand)
                            .build()
                            .execute()
                            .toCompletableFuture()
                            .get();
                    if (processInstance.getExitCode() == 0) {
                        state.setCommand(containerCommand);
                        state.setChecked(true);
                        return containerCommand;
                    }
                }
                state.setException(new IllegalStateException("No container command has been detected"));
            } catch (final Exception exception) {
                state.setException(exception);
            } finally {
                state.setChecked(true);
                detectCommandLock.unlock();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return getCheckedStateContainerCommand(state);
    }

    public static String detectContainerCommand(final ContainerCommandState state) {
        return INSTANCE.detectContainerCommandInternal(state);
    }
}
