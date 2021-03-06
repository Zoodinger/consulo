/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.module.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.consulo.util.pointers.NamedPointerImpl;
import org.consulo.util.pointers.NamedPointerManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.RequiredReadAction;

import java.util.List;

/**
 * @author nik
 */
public class ModulePointerManagerImpl extends NamedPointerManagerImpl<Module> implements ModulePointerManager {
  private final Project myProject;

  public ModulePointerManagerImpl(Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(ProjectTopics.MODULES, new ModuleAdapter() {
      @Override
      public void beforeModuleRemoved(Project project, Module module) {
        unregisterPointer(module);
      }

      @Override
      public void moduleAdded(Project project, Module module) {
        updatePointers(module);
      }

      @Override
      public void modulesRenamed(Project project, List<Module> modules) {
        for (Module module : modules) {
          updatePointers(module);
        }
      }
    });
  }

  @Override
  protected void registerPointer(final Module value, final NamedPointerImpl<Module> pointer) {
    super.registerPointer(value, pointer);

    Disposer.register(value, new Disposable() {
      @Override
      public void dispose() {
        unregisterPointer(value);
      }
    });
  }

  @Nullable
  @Override
  @RequiredReadAction
  public Module findByName(@NotNull String name) {
    return ModuleManager.getInstance(myProject).findModuleByName(name);
  }
}
