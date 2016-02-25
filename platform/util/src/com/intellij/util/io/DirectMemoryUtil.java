/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.intellij.util.io;

import com.intellij.util.containers.ContainerUtil;
import org.consulo.lombok.annotations.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;
import java.util.List;

/**
 * https://svn.apache.org/repos/asf/flume/trunk/flume-ng-core/src/main/java/org/apache/flume/tools/DirectMemoryUtils.java
 */
@Logger
class DirectMemoryUtil {
  private static final String MAX_DIRECT_MEMORY_PARAM = "-XX:MaxDirectMemorySize=";
  private static final long DEFAULT_SIZE = getDefaultDirectMemorySize();

  public static long getDirectMemorySize() {
    RuntimeMXBean runtimemxBean = ManagementFactory.getRuntimeMXBean();
    List<String> arguments = ContainerUtil.reverse(runtimemxBean.getInputArguments());
    long multiplier = 1; //for the byte case.
    for (String s : arguments) {
      if (s.contains(MAX_DIRECT_MEMORY_PARAM)) {
        String memSize = s.toLowerCase().replace(MAX_DIRECT_MEMORY_PARAM.toLowerCase(), "").trim();

        if (memSize.contains("k")) {
          multiplier = 1024;
        }
        else if (memSize.contains("m")) {
          multiplier = 1048576;
        }
        else if (memSize.contains("g")) {
          multiplier = 1073741824;
        }
        memSize = memSize.replaceAll("[^\\d]", "");
        long retValue = Long.parseLong(memSize);
        return retValue * multiplier;
      }
    }
    return DEFAULT_SIZE;
  }

  /**
   * On Java9 = jdk.internal.misc.VM
   * Before Java9 = sun.misc.VM
   */
  private static long getDefaultDirectMemorySize() {
    try {
      Class<?> vmClass = null;
      try {
        vmClass = Class.forName("sun.misc.VM");
      }
      catch (ClassNotFoundException e) {
        vmClass = Class.forName("jdk.internal.misc.VM");
      }

      Method maxDirectMemory = vmClass.getDeclaredMethod("maxDirectMemory");
      Object result = maxDirectMemory.invoke(null);
      if (result != null && result instanceof Long) {
        return (Long)result;
      }
    }
    catch (Throwable e) {
      LOGGER.warn("Unable to get maxDirectMemory from VM: " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    // default according to VM.maxDirectMemory()
    return Runtime.getRuntime().maxMemory();
  }
}
