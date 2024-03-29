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
package org.jetbrains.idea.svn.rollback;

import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ContentRevision;

import javax.annotation.Nonnull;
import java.util.Comparator;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 12/3/12
 * Time: 4:40 PM
 */
public class ChangesAfterPathComparator implements Comparator<Change> {
  private final static ChangesAfterPathComparator ourInstance = new ChangesAfterPathComparator();
  private final static Comparator<ContentRevision> ourComparator = new Comparator<ContentRevision>() {
    @Override
    public int compare(@Nonnull ContentRevision o1, @Nonnull ContentRevision o2) {
      return FileUtil.compareFiles(o1.getFile().getIOFile(), o2.getFile().getIOFile());
    }
  };

  public static ChangesAfterPathComparator getInstance() {
    return ourInstance;
  }

  @Override
  public int compare(Change o1, Change o2) {
    final ContentRevision ar1 = o1.getAfterRevision();
    final ContentRevision ar2 = o2.getAfterRevision();
    return Comparing.compare(ar1, ar2, ourComparator);
  }
}
