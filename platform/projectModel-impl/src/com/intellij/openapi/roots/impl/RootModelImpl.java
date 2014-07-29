/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.consulo.module.extension.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author dsl
 */
@org.consulo.lombok.annotations.Logger
public class RootModelImpl extends RootModelBase implements ModifiableRootModel {
  final ModuleRootManagerImpl myModuleRootManager;
  private boolean myWritable;
  private boolean myDisposed = false;

  private final RootConfigurationAccessor myConfigurationAccessor;

  private final ProjectRootManagerImpl myProjectRootManager;
  // have to register all child disposables using this fake object since all clients just call ModifiableModel.dispose()
  private final CompositeDisposable myDisposable = new CompositeDisposable();

  private String myCurrentLayerName;
  private final Map<String, ModuleRootLayerImpl> myLayers = new TreeMap<String, ModuleRootLayerImpl>();

  RootModelImpl(@NotNull ModuleRootManagerImpl moduleRootManager, @NotNull ProjectRootManagerImpl projectRootManager) {
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;

    myWritable = false;

    try {
      initDefaultLayer(null, projectRootManager);
    }
    catch (InvalidDataException e) {
      //
    }

    myConfigurationAccessor = new RootConfigurationAccessor();
  }

  RootModelImpl(@NotNull Element element,
                @NotNull ModuleRootManagerImpl moduleRootManager,
                @NotNull ProjectRootManagerImpl projectRootManager,
                boolean writable) throws InvalidDataException {
    myProjectRootManager = projectRootManager;
    myModuleRootManager = moduleRootManager;

    loadState(element);

    myWritable = writable;

    myConfigurationAccessor = new RootConfigurationAccessor();
  }

  //creates modifiable model
  RootModelImpl(@NotNull RootModelImpl rootModel,
                @NotNull ModuleRootManagerImpl moduleRootManager,
                @NotNull RootConfigurationAccessor rootConfigurationAccessor,
                @NotNull ProjectRootManagerImpl projectRootManager) {
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;
    myWritable = true;
    myConfigurationAccessor = rootConfigurationAccessor;

    myLayers.clear();
    for (Map.Entry<String, ModuleRootLayerImpl> entry : rootModel.myLayers.entrySet()) {
      ModuleRootLayerImpl moduleRootLayer = new ModuleRootLayerImpl(entry.getValue(), this, projectRootManager);
      myLayers.put(entry.getKey(), moduleRootLayer);
    }
    myCurrentLayerName = rootModel.myCurrentLayerName;
  }

  private void initDefaultLayer(Element element, ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    myCurrentLayerName = "Default";

    ModuleRootLayerImpl moduleRootLayer = new ModuleRootLayerImpl(null, this, projectRootManager);
    myLayers.put(myCurrentLayerName, moduleRootLayer);

    if (element != null) {
      moduleRootLayer.readExternal(element);
    }
    else {

      moduleRootLayer.init();
    }
  }

  public void loadState(Element element) throws InvalidDataException {
    String currentLayer = element.getAttributeValue("current-layer");
    if (currentLayer != null) {
      myCurrentLayerName = currentLayer;

      for (Element moduleLayerElement : element.getChildren("module-layer")) {
        String name = moduleLayerElement.getAttributeValue("name");

        ModuleRootLayerImpl moduleRootLayer = new ModuleRootLayerImpl(null, this, myProjectRootManager);
        moduleRootLayer.readExternal(moduleLayerElement);

        myLayers.put(name, moduleRootLayer);
      }
    }

    // old format - create default profile and load it
    if (myLayers.isEmpty()) {
      initDefaultLayer(element, myProjectRootManager);
    }
  }

  public void putState(Element parent) {
    parent.setAttribute("current-layer", myCurrentLayerName);

    for (Map.Entry<String, ModuleRootLayerImpl> entry : myLayers.entrySet()) {
      Element element = new Element("module-layer");
      element.setAttribute("name", entry.getKey());

      entry.getValue().writeExternal(element);

      parent.addContent(element);
    }
  }

  @Override
  public boolean isWritable() {
    return myWritable;
  }

  public RootConfigurationAccessor getConfigurationAccessor() {
    return myConfigurationAccessor;
  }

  @Override
  public void removeContentEntry(@NotNull ContentEntry entry) {
    assertWritable();
    getCurrentLayer().removeContentEntry(entry);
  }

  @Override
  public void addOrderEntry(@NotNull OrderEntry entry) {
    assertWritable();
    getCurrentLayer().addOrderEntry(entry);
  }

  @NotNull
  @Override
  public LibraryOrderEntry addLibraryEntry(@NotNull Library library) {
    assertWritable();
    return getCurrentLayer().addLibraryEntry(library);
  }

  @NotNull
  @Override
  public ModuleExtensionWithSdkOrderEntry addModuleExtensionSdkEntry(@NotNull ModuleExtensionWithSdk<?> moduleExtension) {
    assertWritable();
    return getCurrentLayer().addModuleExtensionSdkEntry(moduleExtension);
  }

  @NotNull
  @Override
  public LibraryOrderEntry addInvalidLibrary(@NotNull String name, @NotNull String level) {
    assertWritable();
    return getCurrentLayer().addInvalidLibrary(name, level);
  }

  @NotNull
  @Override
  public ModuleOrderEntry addModuleOrderEntry(@NotNull Module module) {
    assertWritable();
    return getCurrentLayer().addModuleOrderEntry(module);
  }

  @NotNull
  @Override
  public ModuleOrderEntry addInvalidModuleEntry(@NotNull String name) {
    assertWritable();
    return getCurrentLayer().addInvalidModuleEntry(name);
  }

  @Nullable
  @Override
  public LibraryOrderEntry findLibraryOrderEntry(@NotNull Library library) {
    return getCurrentLayer().findLibraryOrderEntry(library);
  }

  @Override
  public ModuleExtensionWithSdkOrderEntry findModuleExtensionSdkEntry(@NotNull ModuleExtension extension) {
    return getCurrentLayer().findModuleExtensionSdkEntry(extension);
  }

  @Override
  public void removeOrderEntry(@NotNull OrderEntry entry) {
    assertWritable();
    getCurrentLayer().removeOrderEntry(entry);
  }

  @Override
  public void rearrangeOrderEntries(@NotNull OrderEntry[] newEntries) {
    assertWritable();
    getCurrentLayer().rearrangeOrderEntries(newEntries);
  }

  @Override
  public void clear() {
    disposeLayers();

    try {
      initDefaultLayer(null, myProjectRootManager);
    }
    catch (InvalidDataException e) {
      //
    }
  }

  private void disposeLayers() {
    for (ModuleRootLayerImpl moduleRootLayer : myLayers.values()) {
      moduleRootLayer.dispose();
    }
    myLayers.clear();
  }

  @Override
  public void commit() {
    myModuleRootManager.commitModel(this);
    myWritable = false;
  }

  @SuppressWarnings("unchecked")
  public void doCommit() {
    assert isWritable();

    RootModelImpl sourceModel = getSourceModel();

    sourceModel.myCurrentLayerName = myCurrentLayerName;

    // first we commit changed and new layers
    for (Map.Entry<String, ModuleRootLayerImpl> entry : myLayers.entrySet()) {
      ModuleRootLayerImpl moduleRootLayer = sourceModel.myLayers.get(entry.getKey());
      // if layer exists
      if (moduleRootLayer == null) {
        moduleRootLayer = new ModuleRootLayerImpl(null, sourceModel, myProjectRootManager);
        sourceModel.myLayers.put(entry.getKey(), moduleRootLayer);
      }

      entry.getValue().copy(moduleRootLayer, myCurrentLayerName.equals(entry.getKey()));
    }

    List<String> toRemove = new SmartList<String>();
    // second remove non existed layers
    for (String layerName : sourceModel.myLayers.keySet()) {
      ModuleRootLayerImpl moduleRootLayer = myLayers.get(layerName);
      if (moduleRootLayer == null) {
        toRemove.add(layerName);
      }
    }

    for (String layerName : toRemove) {
      ModuleRootLayerImpl removed = sourceModel.myLayers.remove(layerName);
      assert removed != null;
      removed.dispose();
    }
  }

  @Override
  @NotNull
  public LibraryTable getModuleLibraryTable() {
    return getCurrentLayer().getModuleLibraryTable();
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProjectRootManager.getProject();
  }

  @Override
  @NotNull
  public ContentEntry addContentEntry(@NotNull VirtualFile file) {
    return getCurrentLayer().addContentEntry(file);
  }

  @Override
  @NotNull
  public ContentEntry addContentEntry(@NotNull String url) {
    return getCurrentLayer().addContentEntry(url);
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public <T extends OrderEntry> void replaceEntryOfType(@NotNull Class<T> entryClass, @Nullable final T entry) {
    assertWritable();
    getCurrentLayer().replaceEntryOfType(entryClass, entry);
  }

  public void assertWritable() {
    LOGGER.assertTrue(myWritable);
  }

  public boolean isDependsOn(final Module module) {
    for (OrderEntry entry : getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final Module module1 = ((ModuleOrderEntry)entry).getModule();
        if (module1 == module) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  @NotNull
  public Module getModule() {
    return myModuleRootManager.getModule();
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean isChanged() {
    if (!myWritable) return false;

    for (ModuleRootLayer moduleRootLayer : getLayers()) {
      LOGGER.assertTrue(moduleRootLayer instanceof ModifiableModuleRootLayer);
      if (((ModifiableModuleRootLayer)moduleRootLayer).isChanged()) {
        return true;
      }
    }
    return false;
  }

  void makeExternalChange(@NotNull Runnable runnable) {
    if (myWritable || myDisposed) return;
    myModuleRootManager.makeRootsChange(runnable);
  }

  @Override
  public void dispose() {
    assert !myDisposed;
    Disposer.dispose(myDisposable);
    disposeLayers();
    myWritable = false;
    myDisposed = true;
  }

  private RootModelImpl getSourceModel() {
    assertWritable();
    return myModuleRootManager.getRootModel();
  }

  @NotNull
  @Override
  public ModuleRootLayerImpl getCurrentLayer() {
    ModuleRootLayerImpl moduleRootLayer = myLayers.get(myCurrentLayerName);
    LOGGER.assertTrue(moduleRootLayer != null);
    return moduleRootLayer;
  }

  @NotNull
  @Override
  public ModuleRootLayer[] getLayers() {
    return myLayers.values().toArray(new ModuleRootLayer[myLayers.size()]);
  }

  @Nullable
  @Override
  public ModuleRootLayer findLayerByName(@NotNull String name) {
    return myLayers.get(name);
  }

  void registerOnDispose(@NotNull Disposable disposable) {
    myDisposable.add(disposable);
  }
}
