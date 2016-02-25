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

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import java.awt.*;

@Deprecated
public class XSwingUtilities2 {
  @Deprecated
  public static FontMetrics getFontMetrics(@NotNull JComponent c, Graphics g) {
    return getFontMetrics(c, g, g.getFont());
  }

  @Deprecated
  public static FontMetrics getFontMetrics(@NotNull JComponent c, Graphics g, Font font) {
    if (c != null) {
      // Note: We assume that we're using the FontMetrics
      // from the widget to layout out text, otherwise we can get
      // mismatches when printing.
      return c.getFontMetrics(font);
    }
    return Toolkit.getDefaultToolkit().getFontMetrics(font);
  }

  @Deprecated
  public static void drawStringUnderlineCharAt(JComponent c, Graphics g, String text, int underlinedIndex, int x, int y) {

    BasicGraphicsUtils.drawStringUnderlineCharAt(g, text, underlinedIndex, x, y);
  }

  @Deprecated
  public static void drawString(Component c, Graphics2D g2, String text, int x, int y) {
    BasicGraphicsUtils.drawString(g2, text, 0, x, y);
  }
}
