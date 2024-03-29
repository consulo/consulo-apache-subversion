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
package org.jetbrains.idea.svn;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class NestedCopiesHolder {

  private final Set<NestedCopyInfo> mySet = new HashSet<>();

  public synchronized void add(@Nonnull final Set<NestedCopyInfo> data) {
    mySet.addAll(data);
  }

  public synchronized Set<NestedCopyInfo> getAndClear() {
    Set<NestedCopyInfo> copy = new HashSet<>(mySet);
    mySet.clear();

    return copy;
  }
}
