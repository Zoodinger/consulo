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
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.mustbe.consulo.RequiredDispatchThread;

/**
 * @author Vladimir Kondratyev
 */
public class RefCardAction extends AnAction implements DumbAware {
  @RequiredDispatchThread
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    BrowserUtil.browse("https://github.com/consulo/consulo/wiki/Default-Keymap");
  }

  @RequiredDispatchThread
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    boolean atWelcome = ActionPlaces.WELCOME_SCREEN.equals(e.getPlace());
    e.getPresentation().setIcon(atWelcome ? AllIcons.General.DefaultKeymap : null);
  }
}
