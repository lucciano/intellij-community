/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.util.Function;
import org.gradle.tooling.*;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.idea.BasicIdeaProject;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 5:11 PM
 */
public class GradleExecutionHelper {

  @NotNull
  public ModelBuilder<? extends IdeaProject> getModelBuilder(@NotNull final ExternalSystemTaskId id,
                                                             @Nullable GradleExecutionSettings settings,
                                                             @NotNull ProjectConnection connection,
                                                             boolean downloadLibraries)
  {
    return getModelBuilder(downloadLibraries ? IdeaProject.class : BasicIdeaProject.class, id, settings, connection);
  }
  
  @NotNull
  public <T> ModelBuilder<T> getModelBuilder(@NotNull Class<T> modelType,
                                             @NotNull final ExternalSystemTaskId id,
                                             @Nullable GradleExecutionSettings settings,
                                             @NotNull ProjectConnection connection)
  {
    ModelBuilder<T> result = connection.model(modelType);
    prepare(result, id, settings);
    return result;
  }

  @NotNull
  public BuildLauncher getBuildLauncher(@NotNull final ExternalSystemTaskId id,
                                        @NotNull ProjectConnection connection,
                                        @Nullable GradleExecutionSettings settings)
  {
    BuildLauncher result = connection.newBuild();
    prepare(result, id, settings);
    return result;
  }

  private static void prepare(LongRunningOperation operation,
                              @NotNull final ExternalSystemTaskId id,
                              @Nullable GradleExecutionSettings settings)
  {
    if (settings == null) {
      return;
    }
    final ExternalSystemTaskNotificationListener notificationListener = settings.getNotificationListener();
    if (notificationListener == null) {
      return;
    }

    notificationListener.onStart(id);
    final String javaHome = settings.getJavaHome();
    if (javaHome != null && new File(javaHome).isDirectory()) {
      operation.setJavaHome(new File(javaHome));
    }
    operation.addProgressListener(new ProgressListener() {
      @Override
      public void statusChanged(ProgressEvent event) {
        notificationListener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, event.getDescription()));
      }
    });
  }

  public <T> T execute(@NotNull String projectPath, @Nullable GradleExecutionSettings settings, @NotNull Function<ProjectConnection, T> f) {
    ProjectConnection connection = getConnection(projectPath, settings);
    try {
      return f.fun(connection);
    }
    catch (Throwable e) {
      throw new ExternalSystemException(e);
    }
    finally {
      try {
        connection.close();
      }
      catch (Throwable e) {
        // ignore
      }
    }
  }
  
  /**
   * Allows to retrieve gradle api connection to use for the given project.
   *
   * @param projectPath     target project path
   * @param settings        execution settings to use
   * @return                connection to use
   * @throws IllegalStateException    if it's not possible to create the connection
   */
  @NotNull
  private static ProjectConnection getConnection(@NotNull String projectPath, @Nullable GradleExecutionSettings settings)
    throws IllegalStateException
  {
    File projectFile = new File(projectPath);
    if (!projectFile.isFile()) {
      throw new IllegalArgumentException(GradleBundle.message("gradle.import.text.error.invalid.path", projectPath));
    }
    File projectDir = projectFile.getParentFile();
    GradleConnector connector = GradleConnector.newConnector();
    if (settings != null) {

      // Setup wrapper/local installation usage.
      if (!settings.isUseWrapper()) {
        String gradleHome = settings.getGradleHome();
        if (gradleHome != null) {
          try {
            // There were problems with symbolic links processing at the gradle side.
            connector.useInstallation(new File(gradleHome).getCanonicalFile());
          }
          catch (IOException e) {
            connector.useInstallation(new File(settings.getGradleHome()));
          }
        }
      }

      // Setup service directory if necessary.
      String serviceDirectory = settings.getServiceDirectory();
      if (serviceDirectory != null) {
        connector.useGradleUserHomeDir(new File(serviceDirectory));
      }

      // Setup logging if necessary.
      if (settings.isVerboseProcessing() && connector instanceof DefaultGradleConnector) {
        ((DefaultGradleConnector)connector).setVerboseLogging(true);
      }

      // Setup daemon ttl if necessary.
      long ttl = settings.getRemoteProcessIdleTtlInMs();
      if (ttl > 0 && connector instanceof DefaultGradleConnector) {
        ((DefaultGradleConnector)connector).daemonMaxIdleTime((int)ttl, TimeUnit.MILLISECONDS);
      }
    }
    connector.forProjectDirectory(projectDir);
    ProjectConnection connection = connector.connect();
    if (connection == null) {
      throw new IllegalStateException(String.format(
        "Can't create connection to the target project via gradle tooling api. Project path: '%s'", projectPath
      ));
    }
    return connection;
  }
}
