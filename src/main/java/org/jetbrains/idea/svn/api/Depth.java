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

import org.tmatesoft.svn.core.SVNDepth;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
public enum Depth {

  UNKNOWN("unknown"),
  INFINITY("infinity"),
  IMMEDIATES("immediates"),
  FILES("files"),
  EMPTY("empty");

  @Nonnull
  private static final Map<String, Depth> ourAllDepths = new HashMap<>();

  static {
    for (Depth action : Depth.values()) {
      register(action);
    }
  }

  @Nonnull
  private final String myName;

  Depth(@Nonnull String name) {
    myName = name;
  }

  @Nonnull
  public String getName() {
    return myName;
  }

  @Override
  public String toString() {
    return myName;
  }

  private static void register(@Nonnull Depth depth) {
    ourAllDepths.put(depth.myName, depth);
  }

  @Nonnull
  public static Depth from(@Nonnull String depthName) {
    Depth result = ourAllDepths.get(depthName);

    if (result == null) {
      throw new IllegalArgumentException("Unknown depth " + depthName);
    }

    return result;
  }

  @Nonnull
  public static Depth from(@Nullable SVNDepth depth) {
    return depth != null ? from(depth.getName()) : UNKNOWN;
  }

  @Nonnull
  public static Depth allOrFiles(boolean recursive) {
    return recursive ? INFINITY : FILES;
  }

  @Nonnull
  public static Depth allOrEmpty(boolean recursive) {
    return recursive ? INFINITY : EMPTY;
  }

  public static boolean isRecursive(@Nullable Depth depth) {
    return depth == null || depth == INFINITY || depth == UNKNOWN;
  }
}
