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
package com.intellij.openapi.components.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageOperation;
import com.intellij.openapi.components.impl.stores.StateStorageManagerImpl;
import com.intellij.openapi.components.impl.stores.StorageData;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformLangTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.RequiredDispatchThread;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author mike
 */
public class StateStorageManagerImplTest extends LightPlatformLangTestCase {
  private StateStorageManagerImpl myStateStorageManager;

  @RequiredDispatchThread
  @Override
  public final void setUp() throws Exception {
    super.setUp();
    myStateStorageManager = new StateStorageManagerImpl(null, "foo", null, ApplicationManager.getApplication().getPicoContainer()) {
      @Override
      protected StorageData createStorageData(@NotNull String fileSpec, @NotNull String filePath) {
        throw new UnsupportedOperationException("Method createStorageData not implemented in " + getClass());
      }

      @Nullable
      @Override
      protected String getOldStorageSpec(@NotNull Object component, @NotNull String componentName, @NotNull StateStorageOperation operation) {
        throw new UnsupportedOperationException("Method getOldStorageSpec not implemented in " + getClass());
      }
    };
    myStateStorageManager.addMacro("$MACRO1$", "/temp/m1");
  }

  @Override
  public void tearDown() throws Exception {
    Disposer.dispose(myStateStorageManager);
    super.tearDown();
  }

  public void testCreateFileStateStorageMacroSubstituted() {
    StateStorage data = myStateStorageManager.getStateStorage("$MACRO1$/test.xml", RoamingType.PER_USER);
    assertThat(data, is(notNullValue()));
  }

  public void testCreateStateStorageAssertionThrownWhenUnknownMacro() {
    try {
      myStateStorageManager.getStateStorage("$UNKNOWN_MACRO$/test.xml", RoamingType.PER_USER);
      fail("Exception expected");
    }
    catch (IllegalArgumentException e) {
      assertEquals("Unknown macro: $UNKNOWN_MACRO$ in storage file spec: $UNKNOWN_MACRO$/test.xml", e.getMessage());
    }
  }

  public void testCreateFileStateStorageMacroSubstitutedWhenExpansionHas$() {
    myStateStorageManager.addMacro("$DOLLAR_MACRO$", "/temp/d$");
    StateStorage data = myStateStorageManager.getStateStorage("$DOLLAR_MACRO$/test.xml", RoamingType.PER_USER);
    assertThat(data, is(notNullValue()));
  }
}
