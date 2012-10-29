/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.android.builder;

import com.intellij.util.Consumer;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.indices.IgnoredFileIndex;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.*;

/**
 * @author nik
 */
public class AndroidBuildTarget extends BuildTarget<BuildRootDescriptor> {
  private final JpsModule myModule;
  private final TargetType myTargetType;

  public AndroidBuildTarget(@NotNull TargetType targetType, @NotNull JpsModule module) {
    super(targetType);
    myTargetType = targetType;
    myModule = module;
  }

  @Override
  public String getId() {
    return myModule.getName();
  }

  @Override
  public Collection<BuildTarget<?>> computeDependencies(final BuildTargetRegistry targetRegistry) {
    final List<BuildTarget<?>> result = new ArrayList<BuildTarget<?>>();
    if (myTargetType == TargetType.PACKAGING) {
      result.add(new AndroidBuildTarget(TargetType.DEX, myModule));
    }
    addModuleTargets(myModule, result, targetRegistry);
    final JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(myModule).compileOnly();

    enumerator.processModules(new Consumer<JpsModule>() {
      @Override
      public void consume(JpsModule depModule) {
        addModuleTargets(depModule, result, targetRegistry);
      }
    });
    return result;
  }

  private static void addModuleTargets(JpsModule module, List<BuildTarget<?>> result, BuildTargetRegistry targetRegistry) {
    final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);
    result.addAll(targetRegistry.getModuleBasedTargets(module, extension != null && extension.isPackTestCode()
                                                               ? BuildTargetRegistry.ModuleTargetSelector.ALL
                                                               : BuildTargetRegistry.ModuleTargetSelector.PRODUCTION));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AndroidBuildTarget target = (AndroidBuildTarget)o;

    if (!myModule.equals(target.myModule)) return false;
    return myTargetType.equals(target.myTargetType);
  }

  @Override
  public int hashCode() {
    int result = myModule.hashCode();
    result = 31 * result + myTargetType.hashCode();
    return result;
  }

  @NotNull
  @Override
  public List<BuildRootDescriptor> computeRootDescriptors(JpsModel model,
                                                          ModuleExcludeIndex index,
                                                          IgnoredFileIndex ignoredFileIndex,
                                                          BuildDataPaths dataPaths) {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public BuildRootDescriptor findRootDescriptor(String rootId, BuildRootIndex rootIndex) {
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Android " + myTargetType.getPresentableName();
  }

  @NotNull
  @Override
  public Collection<File> getOutputDirs(CompileContext context) {
    return Collections.emptyList();
  }

  public static class TargetType extends BuildTargetType<AndroidBuildTarget> {
    public static final TargetType DEX = new TargetType(AndroidCommonUtils.DEX_BUILD_TARGET_TYPE_ID, "DEX");
    public static final TargetType PACKAGING = new TargetType(AndroidCommonUtils.PACKAGING_BUILD_TARGET_TYPE_ID, "Packaging");

    private final String myPresentableName;

    private TargetType(@NotNull String typeId, @NotNull String presentableName) {
      super(typeId);
      myPresentableName = presentableName;
    }

    @NotNull
    public String getPresentableName() {
      return myPresentableName;
    }

    @NotNull
    @Override
    public List<AndroidBuildTarget> computeAllTargets(@NotNull JpsModel model) {
      if (!AndroidJpsUtil.containsAndroidFacet(model.getProject())) {
        return Collections.emptyList();
      }
      final List<AndroidBuildTarget> targets = new ArrayList<AndroidBuildTarget>();

      for (JpsModule module : model.getProject().getModules()) {
        final JpsAndroidModuleExtension extension = AndroidJpsUtil.getExtension(module);

        if (extension != null && !extension.isLibrary()) {
          targets.add(new AndroidBuildTarget(this, module));
        }
      }
      return targets;
    }

    @NotNull
    @Override
    public BuildTargetLoader<AndroidBuildTarget> createLoader(@NotNull final JpsModel model) {
      final HashMap<String, AndroidBuildTarget> targetMap = new HashMap<String, AndroidBuildTarget>();

      for (AndroidBuildTarget target : computeAllTargets(model)) {
        targetMap.put(target.getId(), target);
      }
      return new BuildTargetLoader<AndroidBuildTarget>() {
        @Nullable
        @Override
        public AndroidBuildTarget createTarget(@NotNull String targetId) {
          return targetMap.get(targetId);
        }
      };
    }
  }
}