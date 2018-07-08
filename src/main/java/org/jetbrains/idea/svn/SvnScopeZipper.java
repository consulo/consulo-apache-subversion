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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import javax.annotation.Nonnull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SvnScopeZipper implements Runnable {

  @Nonnull
  private final VcsDirtyScope myIn;
  @Nonnull
  private final List<FilePath> myRecursiveDirs;
  // instead of set and heavy equals of file path
  @Nonnull
  private final Map<String, MyDirNonRecursive> myNonRecursiveDirs;

  public SvnScopeZipper(@Nonnull VcsDirtyScope in) {
    myIn = in;
    myRecursiveDirs = ContainerUtil.newArrayList(in.getRecursivelyDirtyDirectories());
    myNonRecursiveDirs = ContainerUtil.newHashMap();
  }

  public void run() {
    // if put directly into dirty scope, to access a copy will be created every time
    final Set<FilePath> files = myIn.getDirtyFilesNoExpand();

    for (FilePath file : files) {
      if (file.isDirectory()) {
        final VirtualFile vFile = file.getVirtualFile();
        // todo take care about this 'not valid' - right now keeping things as they used to be
        final MyDirNonRecursive me = createOrGet(file);
        if (vFile != null && vFile.isValid()) {
          for (VirtualFile child : vFile.getChildren()) {
            me.add(VcsUtil.getFilePath(child));
          }
        }
      }
      else {
        final FilePath parent = file.getParentPath();
        if (parent != null) {
          final MyDirNonRecursive item = createOrGet(parent);
          item.add(file);
        }
      }
    }
  }

  @Nonnull
  private MyDirNonRecursive createOrGet(@Nonnull FilePath parent) {
    String key = getKey(parent);
    MyDirNonRecursive result = myNonRecursiveDirs.get(key);

    if (result == null) {
      result = new MyDirNonRecursive(parent);
      myNonRecursiveDirs.put(key, result);
    }

    return result;
  }

  @Nonnull
  public List<FilePath> getRecursiveDirs() {
    return myRecursiveDirs;
  }

  @Nonnull
  public Map<String, MyDirNonRecursive> getNonRecursiveDirs() {
    return myNonRecursiveDirs;
  }

  public static String getKey(@Nonnull FilePath path) {
    return path.getPresentableUrl();
  }

  static class MyDirNonRecursive {

    @Nonnull
	private final FilePath myDir;
    // instead of set and heavy equals of file path
    @Nonnull
	private final Map<String, FilePath> myChildren;

    private MyDirNonRecursive(@Nonnull FilePath dir) {
      myDir = dir;
      myChildren = ContainerUtil.newHashMap();
    }

    public void add(@Nonnull FilePath path) {
      myChildren.put(getKey(path), path);
    }

    @Nonnull
    public Collection<FilePath> getChildrenList() {
      return myChildren.values();
    }

    @Nonnull
    public FilePath getDir() {
      return myDir;
    }
  }
}
