/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.api;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import org.tmatesoft.svn.core.SVNNodeKind;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
@XmlEnum
public enum NodeKind {

  // see comments in LogEntryPath.Builder for cases when "" kind could appear
  @XmlEnumValue("") UNKNOWN("unknown"),
  @XmlEnumValue("file") FILE("file"),
  @XmlEnumValue("dir") DIR("dir"),
  // used in ConflictVersion when node is missing
  @XmlEnumValue("none") NONE("none");

  @Nonnull
  private static final Map<String, NodeKind> ourAllNodeKinds = new HashMap<>();

  static {
    for (NodeKind kind : NodeKind.values()) {
      register(kind);
    }
  }

  @Nonnull
  private final String myKey;

  NodeKind(@Nonnull String key) {
    myKey = key;
  }

  public boolean isFile() {
    return FILE.equals(this);
  }

  public boolean isDirectory() {
    return DIR.equals(this);
  }

  public boolean isNone() {
    return NONE.equals(this);
  }

  @Override
  public String toString() {
    return myKey;
  }

  private static void register(@Nonnull NodeKind kind) {
    ourAllNodeKinds.put(kind.myKey, kind);
  }

  @Nonnull
  public static NodeKind from(@Nonnull String nodeKindName) {
    NodeKind result = ourAllNodeKinds.get(nodeKindName);

    if (result == null) {
      throw new IllegalArgumentException("Unknown node kind " + nodeKindName);
    }

    return result;
  }

  @Nonnull
  public static NodeKind from(@Nonnull SVNNodeKind nodeKind) {
    return from(nodeKind.toString());
  }

  @Nonnull
  public static NodeKind from(boolean isDirectory) {
    return isDirectory ? DIR : FILE;
  }
}
