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
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;
import java.util.Map;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;

public class Intersection {

  @Nonnull
  private final Map<String, String> myListComments = newHashMap();
  @Nonnull
  private final Map<String, List<Change>> myChangesByLists = newHashMap();

  public void add(@Nonnull LocalChangeList list, @Nonnull Change change) {
    myChangesByLists.computeIfAbsent(list.getName(), key -> newArrayList()).add(change);
    myListComments.put(list.getName(), notNull(list.getComment(), list.getName()));
  }

  @Nonnull
  public String getComment(@Nonnull String listName) {
    return myListComments.get(listName);
  }

  @Nonnull
  public Map<String, List<Change>> getChangesByLists() {
    return myChangesByLists;
  }

  public boolean isEmpty() {
    return myChangesByLists.isEmpty();
  }

  @Nonnull
  public List<Change> getAllChanges() {
    return concat(myChangesByLists.values());
  }

  public static boolean isEmpty(@Nullable Intersection intersection) {
    return intersection == null || intersection.isEmpty();
  }
}
