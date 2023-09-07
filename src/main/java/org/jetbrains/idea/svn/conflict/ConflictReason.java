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
package org.jetbrains.idea.svn.conflict;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
public enum ConflictReason {

  EDITED("edit", "edited"),
  OBSTRUCTED("obstruction", "obstruct", "obstructed"),
  DELETED("delete", "deleted"),
  MISSING("missing", "miss"),
  UNVERSIONED("unversioned", "unversion"),

  /**
   * @since 1.6
   */
  ADDED("add", "added"),

  /**
   * @since 1.7
   */
  REPLACED("replace", "replaced"),

  /**
   * @since 1.8
   */
  MOVED_AWAY("moved-away"),
  MOVED_HERE("moved-here");

  @Nonnull
  private static final Map<String, ConflictReason> ourAllReasons = new HashMap<>();

  static {
    for (ConflictReason reason : ConflictReason.values()) {
      register(reason);
    }
  }

  @Nonnull
  private final String myKey;
  @Nonnull
  private final String[] myOtherKeys;

  ConflictReason(@Nonnull String key, @Nonnull String... otherKeys) {
    myKey = key;
    myOtherKeys = otherKeys;
  }

  @Override
  public String toString() {
    return myKey;
  }

  private static void register(@Nonnull ConflictReason reason) {
    ourAllReasons.put(reason.myKey, reason);

    for (String key : reason.myOtherKeys) {
      ourAllReasons.put(key, reason);
    }
  }

  @Nonnull
  public static ConflictReason from(@Nonnull String reasonName) {
    ConflictReason result = ourAllReasons.get(reasonName);

    if (result == null) {
      throw new IllegalArgumentException("Unknown conflict reason " + reasonName);
    }

    return result;
  }
}
