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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

/**
 *  @author dsl
 */
public class OrderEntryFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderEntryFactory");
  @NonNls public static final String ORDER_ENTRY_ELEMENT_NAME = JpsModuleRootModelSerializer.ORDER_ENTRY_TAG;
  @NonNls public static final String ORDER_ENTRY_TYPE_ATTR = JpsModuleRootModelSerializer.TYPE_ATTRIBUTE;

  static OrderEntry createOrderEntryByElement(Element element, RootModelImpl rootModel, ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    LOG.assertTrue(ORDER_ENTRY_ELEMENT_NAME.equals(element.getName()));
    final String type = element.getAttributeValue(ORDER_ENTRY_TYPE_ATTR);
    if (type == null) {
      throw new InvalidDataException();
    }
    if (ModuleSourceOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new ModuleSourceOrderEntryImpl(element, rootModel);
    }
    else if (ModuleExtensionWithSdkOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new ModuleExtensionWithSdkOrderEntryImpl(element, rootModel, projectRootManager);
    }
    else if (InheritedSdkOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new InheritedSdkOrderEntryImpl(element, rootModel, projectRootManager);
    }
    else if (LibraryOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new LibraryOrderEntryImpl(element, rootModel, projectRootManager);
    }
    else if (ModuleLibraryOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new ModuleLibraryOrderEntryImpl(element, rootModel, projectRootManager);
    }
    else if (ModuleOrderEntryImpl.ENTRY_TYPE.equals(type)) {
      return new ModuleOrderEntryImpl(element, rootModel);
    }
    else throw new InvalidDataException("Unknown order entry type:" + type);
  }

  static Element createOrderEntryElement(String type) {
    final Element element = new Element(ORDER_ENTRY_ELEMENT_NAME);
    element.setAttribute(ORDER_ENTRY_TYPE_ATTR, type);
    return element;
  }

}
