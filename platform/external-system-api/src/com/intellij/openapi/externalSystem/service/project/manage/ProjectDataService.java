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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Defines common contract for a strategy which is able to manage project data defines in terms of 'external systems' sub-system.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * <p/>
 * <b>Note:</b> there is a possible case that more than one service is registered for the same key, e.g. there is a built-in
 * generic service and a plugin provides custom extension to it. Platform calls all of them then. That means that project data
 * service implementation must be ready to the following:
 * <pre>
 * <ul>
 *   <li>target data is already imported by another service (just no-op then);</li>
 *   <li>
 *     provide a hint about the order in which the services for the same key should be processed. That is done by marking
 *     it by {@link Order} annotation where the smaller value corresponds to earlier execution;
 *   </li>
 * </ul>
 * </pre>
 * 
 * 
 * @author Denis Zhdanov
 * @since 4/12/13 3:59 PM
 * @param <T>  target project data type
 */
public interface ProjectDataService<T> {

  ExtensionPointName<ProjectDataService<?>> EP_NAME = ExtensionPointName.create("com.intellij.externalProjectDataService");

  /**
   * @return key of project data supported by the current manager
   */
  @NotNull
  Key<T> getTargetDataKey();

  /**
   * It's assumed that given data nodes present at the ide when this method returns. I.e. the method should behave as below for
   * every of the given data nodes:
   * <pre>
   * <ul>
   *   <li>there is an existing project entity for the given data node and it has the same state. Do nothing for it then;</li>
   *   <li>
   *     there is an existing project entity for the given data node but it has different state (e.g. a module dependency
   *     is configured as 'exported' at the ide but not at external system). Reset the state to the external system's one then;
   *   </li>
   *   <li> there is no corresponding project entity at the ide side. Create it then; </li>
   * </ul>
   * </pre>
   * are created, updated or left as-is if they have the
   * 
   * @param toImport
   * @param project
   * @param synchronous
   */
  void importData(@NotNull Collection<DataNode<T>> toImport, @NotNull Project project, boolean synchronous);

  void removeData(@NotNull Collection<DataNode<T>> toRemove, @NotNull Project project, boolean synchronous);
}
