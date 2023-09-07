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
package org.jetbrains.idea.svn.history;

import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.List;

public interface SvnLogLoader {
  List<CommittedChangeList> loadInterval(final SVNRevision fromIncluding, final SVNRevision toIncluding,
										 final int maxCount, final boolean includingYoungest, final boolean includeOldest)
    throws VcsException;
}
