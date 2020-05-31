/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.statistics;

import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.idea.svn.NestedCopyType;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnVcs;
import com.intellij.internal.statistic.AbstractApplicationUsagesCollector;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnWorkingCopyFormatUsagesCollector extends AbstractApplicationUsagesCollector {

  private static final String GROUP_ID = "svn working copy format";

  @Nonnull
  public String getGroupId() {
    return GROUP_ID;
  }

  @Nonnull
  public Set<UsageDescriptor> getProjectUsages(@Nonnull Project project) {
    SvnVcs vcs = SvnVcs.getInstance(project);

    // do not track roots with errors (SvnFileUrlMapping.getErrorRoots()) as they are "not usable" until errors are resolved
    // skip externals and switched directories as they will have the same format
    List<RootUrlInfo> roots = ContainerUtil.filter(vcs.getSvnFileUrlMapping().getAllWcInfos(), new Condition<RootUrlInfo>() {
      @Override
      public boolean value(RootUrlInfo info) {
        return info.getType() == null || NestedCopyType.inner.equals(info.getType());
      }
    });

    return ContainerUtil.map2Set(roots, info -> new UsageDescriptor(info.getFormat().toString(), 1));
  }
}
