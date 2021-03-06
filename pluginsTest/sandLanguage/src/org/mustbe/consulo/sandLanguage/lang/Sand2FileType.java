/*
 * Copyright 2013-2014 must-be.org
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
package org.mustbe.consulo.sandLanguage.lang;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author VISTALL
 * @since 19.03.14
 */
public class Sand2FileType extends LanguageFileType {
  public static Sand2FileType INSTANCE = new Sand2FileType();

  private Sand2FileType() {
    super(SandLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "SAND2";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Sand2 files";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "sand2";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.ClassInitializer;
  }
}
