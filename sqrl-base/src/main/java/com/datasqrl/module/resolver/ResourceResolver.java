package com.datasqrl.module.resolver;

import com.datasqrl.canonicalizer.NamePath;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface ResourceResolver {

  List<URI> loadPath(NamePath namePath);

  Optional<URI> resolveFile(NamePath namePath);
}