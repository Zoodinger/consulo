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
package com.intellij.ide.highlighter;

import org.jetbrains.annotations.NonNls;
import org.mustbe.consulo.DeprecationInfo;

@Deprecated
@DeprecationInfo(value = "Project file type is not supported anymore", until = "1.0")
public class ProjectFileType  {
  @NonNls
  public static final String DEFAULT_EXTENSION = "ipr";
  @NonNls
  public static final String DOT_DEFAULT_EXTENSION = ".ipr";
}
