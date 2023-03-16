package com.datasqrl.loaders;

import com.datasqrl.function.builtin.time.StdLibrary;
import com.datasqrl.name.NamePath;
import com.datasqrl.plan.local.generate.NamespaceObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class StandardLibraryLoader {

  private final Map<NamePath, StdLibrary> standardLibrary;

  public StandardLibraryLoader() {
    Map<NamePath, StdLibrary> standardLibrary = new HashMap<>();
    ServiceLoader<StdLibrary> serviceLoader = ServiceLoader.load(StdLibrary.class);
    for (StdLibrary handler : serviceLoader) {
      standardLibrary.put(handler.getPath(), handler);
    }
    this.standardLibrary = standardLibrary;
  }

  public List<NamespaceObject> load(NamePath namePath) {
    if (standardLibrary.containsKey(namePath)) {
      return standardLibrary.get(namePath).getNamespaceObjects();
    }
    return List.of();
  }
}