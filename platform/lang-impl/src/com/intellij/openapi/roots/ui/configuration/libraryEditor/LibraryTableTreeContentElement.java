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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.RequiredDispatchThread;

import java.awt.*;

public abstract class LibraryTableTreeContentElement<E> extends NodeDescriptor<E> {
  protected LibraryTableTreeContentElement(@Nullable NodeDescriptor parentDescriptor) {
    super(null, parentDescriptor);
  }

  protected static Color getForegroundColor(boolean isValid) {
    return isValid ? UIUtil.getListForeground() : JBColor.RED;
  }

  @RequiredDispatchThread
  @Override
  public boolean update() {
    return false;
  }

  @Override
  public E getElement() {
    return (E)this;
  }
}
