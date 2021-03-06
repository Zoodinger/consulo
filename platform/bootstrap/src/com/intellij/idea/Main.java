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
package com.intellij.idea;

import com.intellij.ide.Bootstrap;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Restarter;
import org.mustbe.consulo.SharedConstants;
import org.mustbe.consulo.application.ApplicationProperties;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"UseOfSystemOutOrSystemErr", "MethodNamesDifferingOnlyByCase"})
public class Main {
  public static final int UPDATE_FAILED = 1;
  public static final int STARTUP_EXCEPTION = 2;
  public static final int STARTUP_IMPOSSIBLE = 3;
  public static final int PLUGIN_ERROR = 4;

  private static final String AWT_HEADLESS = "java.awt.headless";

  private static boolean isHeadless;
  private static boolean isCommandLine;

  private Main() {
  }

  public static void main(final String[] args) {
    setFlags(args);

    if (isHeadless()) {
      System.setProperty(AWT_HEADLESS, Boolean.TRUE.toString());
    }
    else {
      if (GraphicsEnvironment.isHeadless()) {
        throw new HeadlessException("Unable to detect graphics environment");
      }

      if (args.length == 0) {
        try {
          installPatch();
        }
        catch (Throwable t) {
          showMessage("Update Failed", t);
          System.exit(UPDATE_FAILED);
        }
      }
    }

    try {
      Bootstrap.main(args, Main.class.getName() + "Impl", "start");
    }
    catch (Throwable t) {
      showMessage("Start Failed", t);
      System.exit(STARTUP_EXCEPTION);
    }
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  public static boolean isCommandLine() {
    return isCommandLine;
  }

  public static void setFlags(String[] args) {
    isHeadless = isCommandLine = isHeadless(args);
  }

  private static boolean isHeadless(String[] args) {
    return Boolean.getBoolean(AWT_HEADLESS) || Boolean.getBoolean(ApplicationProperties.CONSULO_IN_UNIT_TEST);
  }

  private static void installPatch() throws IOException {
    File originalPatchFile = new File(System.getProperty("java.io.tmpdir"), SharedConstants.PATCH_FILE_NAME);
    File copyPatchFile = new File(System.getProperty("java.io.tmpdir"), SharedConstants.PATCH_FILE_NAME + "_copy");

    // always delete previous patch copy
    if (!FileUtilRt.delete(copyPatchFile)) {
      throw new IOException("Cannot create temporary patch file");
    }

    if (!originalPatchFile.exists()) {
      return;
    }

    if (!originalPatchFile.renameTo(copyPatchFile) || !FileUtilRt.delete(originalPatchFile)) {
      throw new IOException("Cannot create temporary patch file");
    }

    int status = 0;
    if (Restarter.isSupported()) {
      List<String> args = new ArrayList<String>();

      if (SystemInfoRt.isWindows) {
        File launcher = new File(PathManager.getBinPath(), "VistaLauncher.exe");
        args.add(Restarter.createTempExecutable(launcher).getPath());
      }

      Collections.addAll(args, System.getProperty("java.home") + "/bin/java", "-Xmx500m", "-classpath", copyPatchFile.getPath(), "com.intellij.updater.Runner",
                         "install", PathManager.getHomePath());

      status = Restarter.scheduleRestart(ArrayUtilRt.toStringArray(args));
    }
    else {
      String message = "Patch update is not supported - please do it manually";
      showMessage("Update Error", message, true);
    }

    System.exit(status);
  }

  public static void showMessage(String title, Throwable t) {
    StringWriter message = new StringWriter();
    message.append("Internal error. Please report to https://github.com/consulo/consulo/issues\n\n");
    t.printStackTrace(new PrintWriter(message));
    showMessage(title, message.toString(), true);
  }

  @SuppressWarnings({"UseJBColor", "UndesirableClassUsage"})
  public static void showMessage(String title, String message, boolean error) {
    if (isCommandLine()) {
      PrintStream stream = error ? System.err : System.out;
      stream.println("\n" + title + ": " + message);
    }
    else {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      }
      catch (Throwable ignore) {
      }

      JTextPane textPane = new JTextPane();
      textPane.setEditable(false);
      textPane.setText(message.replaceAll("\t", "    "));
      textPane.setBackground(Color.white);
      textPane.setCaretPosition(0);
      JScrollPane scrollPane = new JScrollPane(textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

      int maxHeight = Toolkit.getDefaultToolkit().getScreenSize().height - 150;
      Dimension component = scrollPane.getPreferredSize();
      if (component.height >= maxHeight) {
        Object setting = UIManager.get("ScrollBar.width");
        int width = setting instanceof Integer ? ((Integer)setting).intValue() : 20;
        scrollPane.setPreferredSize(new Dimension(component.width + width, maxHeight));
      }

      int type = error ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE;
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), scrollPane, title, type);
    }
  }
}
