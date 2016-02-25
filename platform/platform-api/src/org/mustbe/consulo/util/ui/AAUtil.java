/*
 * Copyright 2013-2016 must-be.org
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
package org.mustbe.consulo.util.ui;

import com.intellij.ui.ListCellRendererWrapper;
import org.consulo.lombok.annotations.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Constructor;

/**
 * @author VISTALL
 * @since 26.02.2016
 */
@Logger
public class AAUtil {
  public static class AATextInfo {
    private Object renderingHits;
    private int lcdValue;

    public AATextInfo(Object renderingHits, int lcdValue) {
      this.renderingHits = renderingHits;
      this.lcdValue = lcdValue;
    }
  }

  private static Object AA_TEXT_PROPERTY_KEY = null;
  private static Constructor<?> AA_TEXT_INFO_CONSTRUCTOR = null;

  static {
    Class<?> swingUtilities2Class = null;
    Class<?> swingUtilities2AATextInfoClass = null;
    try {
      swingUtilities2Class = Class.forName("sun.swing.SwingUtilities2");
      swingUtilities2AATextInfoClass = Class.forName("sun.swing.SwingUtilities2$AATextInfo");
    }
    catch (Throwable ignored) {
    }

    if (swingUtilities2Class != null && swingUtilities2AATextInfoClass != null) {
      try {
        AA_TEXT_PROPERTY_KEY = swingUtilities2Class.getField("AA_TEXT_PROPERTY_KEY").get(null);
        AA_TEXT_INFO_CONSTRUCTOR = swingUtilities2AATextInfoClass.getConstructor(Object.class, Integer.class);
      }
      catch (Throwable ignored) {
      }
    }
  }

  public static void putAAInfo(@NotNull JComponent component, @Nullable AATextInfo aaTextInfo) {
    if (AA_TEXT_PROPERTY_KEY != null) {
      try {
        Object o = aaTextInfo == null ? null : AA_TEXT_INFO_CONSTRUCTOR.newInstance(aaTextInfo.renderingHits, aaTextInfo.lcdValue);
        component.putClientProperty(AA_TEXT_PROPERTY_KEY, o);
      }
      catch (Exception e) {
        LOGGER.error(e);
      }
    }
    else {
      component.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, aaTextInfo == null ? null : aaTextInfo.renderingHits);
      component.putClientProperty(RenderingHints.KEY_TEXT_LCD_CONTRAST, aaTextInfo == null ? null : aaTextInfo.lcdValue);
    }
  }

  public static void putAAInfo(@NotNull ListCellRendererWrapper component, @Nullable AATextInfo aaTextInfo) {
    if (AA_TEXT_PROPERTY_KEY != null) {
      try {
        Object o = aaTextInfo == null ? null : AA_TEXT_INFO_CONSTRUCTOR.newInstance(aaTextInfo.renderingHits, aaTextInfo.lcdValue);
        component.setClientProperty(AA_TEXT_PROPERTY_KEY, o);
      }
      catch (Exception e) {
        LOGGER.error(e);
      }
    }
    else {
      component.setClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, aaTextInfo == null ? null : aaTextInfo.renderingHits);
      component.setClientProperty(RenderingHints.KEY_TEXT_LCD_CONTRAST, aaTextInfo == null ? null : aaTextInfo.lcdValue);
    }
  }
}
