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
package org.jetbrains.idea.svn.rollback;

import consulo.application.Application;
import consulo.application.util.TempFileService;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.rollback.RollbackProgressListener;
import consulo.versionControlSystem.util.VcsUtil;
import org.jetbrains.idea.svn.SvnFileSystemListener;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.properties.PropertiesMap;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
* @author Konstantin Kolosovsky.
*/
public class Reverter {

  @Nonnull
  private final SvnVcs myVcs;
  private final ProgressTracker myHandler;
  private final List<VcsException> myExceptions;
  private final List<CopiedAsideInfo> myFromToModified;
  private final Map<File, PropertiesMap> myProperties;

  Reverter(@Nonnull SvnVcs vcs, @Nonnull RollbackProgressListener listener, @Nonnull List<VcsException> exceptions) {
    myVcs = vcs;
    myHandler = createRevertHandler(exceptions, listener);
    myExceptions = exceptions;
    myFromToModified = new ArrayList<>();
    myProperties = new HashMap<>();
  }

  public void revert(@Nonnull Collection<File> files, boolean recursive) {
    if (files.isEmpty()) return;

    File target = files.iterator().next();
    try {
      // Files passed here are split into groups by root and working copy format - thus we could determine factory based on first file
      myVcs.getFactory(target).createRevertClient().revert(files, Depth.allOrEmpty(recursive), myHandler);
    }
    catch (VcsException e) {
      processRevertError(e);
    }
  }

  public void moveRenamesToTmp(@Nonnull UnversionedAndNotTouchedFilesGroupCollector collector) {
    try {
      // copy also directories here - for moving with svn
      // also, maybe still use just patching? -> well-tested thing, only deletion of folders might suffer
      // todo: special case: addition + move. mark it
      TempFileService tempFileService = Application.get().getInstance(TempFileService.class);
      final File tmp = tempFileService.createTempDirectory("forRename", "").toFile();
      final PropertyConsumer handler = createPropertyHandler(myProperties, collector);

      for (Map.Entry<File, ThroughRenameInfo> entry : collector.getFromTo().entrySet()) {
        final File source = entry.getKey();
        final ThroughRenameInfo info = entry.getValue();
        if (info.isVersioned()) {
          myVcs.getFactory(source).createPropertyClient().list(SvnTarget.fromFile(source), SVNRevision.WORKING, Depth.EMPTY, handler);
        }
        if (source.isDirectory()) {
          if (!FileUtil.filesEqual(info.getTo(), info.getFirstTo())) {
            myFromToModified.add(new CopiedAsideInfo(info.getParentImmediateReverted(), info.getTo(), info.getFirstTo(), null));
          }
          continue;
        }
        final File tmpFile = FileUtil.createTempFile(tmp, source.getName(), "", false);
        tmpFile.mkdirs();
        FileUtil.delete(tmpFile);
        FileUtil.copy(source, tmpFile, FilePermissionCopier.BY_NIO2);
        myFromToModified.add(new CopiedAsideInfo(info.getParentImmediateReverted(), info.getTo(), info.getFirstTo(), tmpFile));
      }
    }
    catch (IOException e) {
      myExceptions.add(new VcsException(e));
    }
    catch (VcsException e) {
      myExceptions.add(e);
    }
  }

  public void moveGroup() {
    Collections.sort(myFromToModified, new Comparator<CopiedAsideInfo>() {
      @Override
      public int compare(CopiedAsideInfo o1, CopiedAsideInfo o2) {
        return FileUtil.compareFiles(o1.getTo(), o2.getTo());
      }
    });
    for (CopiedAsideInfo info : myFromToModified) {
      if (info.getParentImmediateReverted().exists()) {
        // parent successfully renamed/moved
        try {
          final File from = info.getFrom();
          final File target = info.getTo();
          if (from != null && !FileUtil.filesEqual(from, target) && !target.exists()) {
            SvnFileSystemListener.moveFileWithSvn(myVcs, from, target);
          }
          final File root = info.getTmpPlace();
          if (root == null) continue;
          if (!root.isDirectory()) {
            if (target.exists()) {
              FileUtil.copy(root, target, FilePermissionCopier.BY_NIO2);
            }
            else {
              FileUtil.rename(root, target, FilePermissionCopier.BY_NIO2);
            }
          }
          else {
            FileUtil.processFilesRecursively(root, file -> {
              if (file.isDirectory()) return true;
              String relativePath = FileUtil.getRelativePath(root.getPath(), file.getPath(), File.separatorChar);
              File newFile = new File(target, relativePath);
              newFile.getParentFile().mkdirs();
              try {
                if (target.exists()) {
                  FileUtil.copy(file, newFile, FilePermissionCopier.BY_NIO2);
                }
                else {
                  FileUtil.rename(file, newFile, FilePermissionCopier.BY_NIO2);
                }
              }
              catch (IOException e) {
                myExceptions.add(new VcsException(e));
              }
              return true;
            });
          }
        }
        catch (IOException e) {
          myExceptions.add(new VcsException(e));
        }
        catch (VcsException e) {
          myExceptions.add(e);
        }
      }
    }

    applyProperties();
  }

  private void applyProperties() {
    for (Map.Entry<File, PropertiesMap> entry : myProperties.entrySet()) {
      File file = entry.getKey();
      try {
        myVcs.getFactory(file).createPropertyClient().setProperties(file, entry.getValue());
      }
      catch (VcsException e) {
        myExceptions.add(e);
      }
    }
  }

  private void processRevertError(@Nonnull VcsException e) {
    if (e.getCause() instanceof SVNException) {
      SVNException cause = (SVNException)e.getCause();

      // skip errors on unversioned resources.
      if (cause.getErrorMessage().getErrorCode() != SVNErrorCode.WC_NOT_DIRECTORY) {
        myExceptions.add(e);
      }
    } else {
      myExceptions.add(e);
    }
  }

  @Nonnull
  private static ProgressTracker createRevertHandler(@Nonnull final List<VcsException> exceptions,
                                                     @Nonnull final RollbackProgressListener listener) {
    return new ProgressTracker() {
      public void consume(ProgressEvent event) {
        if (event.getAction() == EventAction.REVERT) {
          final File file = event.getFile();
          if (file != null) {
            listener.accept(file);
          }
        }
        if (event.getAction() == EventAction.FAILED_REVERT) {
          exceptions.add(new VcsException("Revert failed"));
        }
      }

      public void checkCancelled() {
        listener.checkCanceled();
      }
    };
  }

  @Nonnull
  private static PropertyConsumer createPropertyHandler(@Nonnull final Map<File, PropertiesMap> properties,
                                                        @Nonnull final UnversionedAndNotTouchedFilesGroupCollector collector) {
    return new PropertyConsumer() {
      @Override
      public void handleProperty(File path, PropertyData property) throws SVNException {
        final ThroughRenameInfo info = collector.findToFile(VcsUtil.getFilePath(path), null);
        if (info != null) {
          if (!properties.containsKey(info.getTo())) {
            properties.put(info.getTo(), new PropertiesMap());
          }
          properties.get(info.getTo()).put(property.getName(), property.getValue());
        }
      }

      @Override
      public void handleProperty(SVNURL url, PropertyData property) throws SVNException {
      }

      @Override
      public void handleProperty(long revision, PropertyData property) throws SVNException {
      }
    };
  }
}
