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
package com.intellij.idea.starter;

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.junit.runner.JUnitCore;

import java.io.IOException;
import java.util.List;

/**
 * @author VISTALL
 * @since 04.04.2016
 *
 * Used inside com.intellij.idea.IdeaApplication#createStarter(boolean)
 */
public class UnitTestStarter extends ApplicationStarter {
  private static final Logger LOGGER = Logger.getInstance(UnitTestStarter.class);

  @Override
  public void main(String[] args) {
    IdeaPluginDescriptorImpl plugin = (IdeaPluginDescriptorImpl)PluginManager.getPlugin(PluginManagerCore.UNIT_TEST_PLUGIN);
    assert plugin != null;
    PluginClassLoader pluginClassLoader = (PluginClassLoader)plugin.getPluginClassLoader();

    String testData = args[0];
    try {
      List<String> list = FileUtil.loadLines(StringUtil.unquoteString(testData));
      if (list.isEmpty()) {
        System.exit(-1);
        return;
      }

      JUnitCore core = new JUnitCore();
      core.addListener(new SMTestSender());

      for (String className : list) {
        try {
          Class<?> testClass = pluginClassLoader.loadClass(className);
          core.run(testClass);
        }
        catch (ClassNotFoundException e) {
          LOGGER.error(e);
          System.exit(-1);
        }
      }

      System.exit(0);
    }
    catch (IOException e) {
      LOGGER.error(e);
      System.exit(-1);
    }
  }
}
