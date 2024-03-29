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

import consulo.container.boot.ContainerPathManager;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.data.DataExternalizer;
import consulo.logging.Logger;
import consulo.versionControlSystem.VcsException;
import org.jetbrains.idea.svn.SmallMapSerializer;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.CopyData;
import org.jetbrains.idea.svn.history.FirstInBranch;
import org.tmatesoft.svn.core.SVNURL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class SvnBranchPointsCalculator {

  private static final Logger LOG = Logger.getInstance(SvnBranchPointsCalculator.class);

  @Nonnull
  private final SmallMapSerializer<String, TreeMap<String, BranchCopyData>> myPersistentMap;
  @Nonnull
  private final Object myPersistenceLock = new Object();
  @Nonnull
  private final SvnVcs myVcs;

  public SvnBranchPointsCalculator(@Nonnull SvnVcs vcs) {
    myVcs = vcs;
    File directory = new File(new File(ContainerPathManager.get().getSystemPath(), "vcs"), "svn_copy_sources");
    directory.mkdirs();
    File file = new File(directory, myVcs.getProject().getLocationHash());
    myPersistentMap = new SmallMapSerializer<>(file, EnumeratorStringDescriptor.INSTANCE, new BranchDataExternalizer());
  }

  @Nullable
  public WrapperInvertor getBestHit(@Nonnull String repoUrl, @Nonnull String sourceUrl, @Nonnull String targetUrl) {
    synchronized (myPersistenceLock) {
      WrapperInvertor result = null;
      TreeMap<String, BranchCopyData> map = myPersistentMap.get(repoUrl);

      if (map != null) {
        BranchCopyData sourceData = getBranchData(map, sourceUrl);
        BranchCopyData targetData = getBranchData(map, targetUrl);

        if (sourceData != null && targetData != null) {
          boolean inverted = sourceData.getTargetRevision() > targetData.getTargetRevision();
          result = new WrapperInvertor(inverted, inverted ? sourceData : targetData);
        }
        else if (sourceData != null) {
          result = new WrapperInvertor(true, sourceData);
        }
        else if (targetData != null) {
          result = new WrapperInvertor(false, targetData);
        }
      }

      return result;
    }
  }

  public void deactivate() {
    synchronized (myPersistenceLock) {
      myPersistentMap.force();
    }
  }

  private void persist(@Nonnull String repoUrl, @Nonnull BranchCopyData data) {
    // todo - rewrite of rather big piece; consider rewriting
    synchronized (myPersistenceLock) {
      TreeMap<String, BranchCopyData> map = myPersistentMap.get(repoUrl);
      if (map == null) {
        map = new TreeMap<>();
      }
      map.put(data.getTarget(), data);
      myPersistentMap.put(repoUrl, map);
      myPersistentMap.force();
    }
  }

  @Nullable
  private static BranchCopyData getBranchData(@Nonnull NavigableMap<String, BranchCopyData> map, @Nonnull String url) {
    Map.Entry<String, BranchCopyData> branchData = map.floorEntry(url);
    return branchData != null && url.startsWith(branchData.getKey()) ? branchData.getValue() : null;
  }

  private static class BranchDataExternalizer implements DataExternalizer<TreeMap<String, BranchCopyData>> {
    public void save(@Nonnull DataOutput out, @Nonnull TreeMap<String, BranchCopyData> value) throws IOException {
      out.writeInt(value.size());
      for (Map.Entry<String, BranchCopyData> entry : value.entrySet()) {
        out.writeUTF(entry.getKey());
        save(out, entry.getValue());
      }
    }

    private static void save(@Nonnull DataOutput out, @Nonnull BranchCopyData value) throws IOException {
      out.writeUTF(value.getSource());
      out.writeUTF(value.getTarget());
      out.writeLong(value.getSourceRevision());
      out.writeLong(value.getTargetRevision());
    }

    @Nonnull
    public TreeMap<String, BranchCopyData> read(@Nonnull DataInput in) throws IOException {
      TreeMap<String, BranchCopyData> result = new TreeMap<>();
      int size = in.readInt();

      for (int i = 0; i < size; i++) {
        result.put(in.readUTF(), readCopyPoint(in));
      }

      return result;
    }

    @Nonnull
    private static BranchCopyData readCopyPoint(@Nonnull DataInput in) throws IOException {
      String sourceUrl = in.readUTF();
      String targetUrl = in.readUTF();
      long sourceRevision = in.readLong();
      long targetRevision = in.readLong();

      return new BranchCopyData(sourceUrl, sourceRevision, targetUrl, targetRevision);
    }
  }

  public static class WrapperInvertor {
    private final BranchCopyData myWrapped;
    private final boolean myInvertedSense;

    public WrapperInvertor(boolean invertedSense, BranchCopyData wrapped) {
      myInvertedSense = invertedSense;
      myWrapped = wrapped;
    }

    public boolean isInvertedSense() {
      return myInvertedSense;
    }

    public BranchCopyData getWrapped() {
      return myWrapped;
    }

    public BranchCopyData getTrue() {
      return myInvertedSense ? myWrapped.invertSelf() : myWrapped;
    }

    public BranchCopyData inverted() {
      return myWrapped.invertSelf();
    }

    @Override
    public String toString() {
      return "inverted: " + myInvertedSense + " wrapped: " + myWrapped.toString();
    }
  }

  @Nullable
  public WrapperInvertor calculateCopyPoint(@Nonnull SVNURL repoUrl, @Nonnull String sourceUrl, @Nonnull String targetUrl)
    throws VcsException {
    WrapperInvertor result = getBestHit(repoUrl.toDecodedString(), sourceUrl, targetUrl);

    if (result == null) {
      CopyData copyData = new FirstInBranch(myVcs, repoUrl, targetUrl, sourceUrl).run();

      if (copyData != null) {
        BranchCopyData branchCopyData =
          copyData.isTrunkSupposedCorrect()
          ? new BranchCopyData(sourceUrl, copyData.getCopySourceRevision(), targetUrl, copyData.getCopyTargetRevision())
          : new BranchCopyData(targetUrl, copyData.getCopySourceRevision(), sourceUrl, copyData.getCopyTargetRevision());

        persist(repoUrl.toDecodedString(), branchCopyData);
        result = new WrapperInvertor(!copyData.isTrunkSupposedCorrect(), branchCopyData);
      }
    }

    logCopyData(repoUrl.toDecodedString(), sourceUrl, targetUrl, result);

    return result;
  }

  private static void logCopyData(@Nonnull String repoUrl,
                                  @Nonnull String sourceUrl,
                                  @Nonnull String targetUrl,
                                  @Nullable WrapperInvertor inverter) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("repoURL: " + repoUrl + ", sourceUrl:" + sourceUrl + ", targetUrl: " + targetUrl + ", inverter: " + inverter);
    }
  }

  public static class BranchCopyData {
    private final String mySource;
    private final String myTarget;
    private final long mySourceRevision;
    private final long myTargetRevision;

    public BranchCopyData(String source, long sourceRevision, String target, long targetRevision) {
      mySource = source;
      mySourceRevision = sourceRevision;
      myTarget = target;
      myTargetRevision = targetRevision;
    }

    @Override
    public String toString() {
      return "source: " + mySource + "@" + mySourceRevision + " target: " + myTarget + "@" + myTargetRevision;
    }

    public String getSource() {
      return mySource;
    }

    public long getSourceRevision() {
      return mySourceRevision;
    }

    public String getTarget() {
      return myTarget;
    }

    public long getTargetRevision() {
      return myTargetRevision;
    }

    public BranchCopyData invertSelf() {
      return new BranchCopyData(myTarget, myTargetRevision, mySource, mySourceRevision);
    }
  }
}
