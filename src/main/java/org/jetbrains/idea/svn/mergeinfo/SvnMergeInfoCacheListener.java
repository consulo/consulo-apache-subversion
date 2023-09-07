package org.jetbrains.idea.svn.mergeinfo;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;

@TopicAPI(ComponentScope.PROJECT)
public interface SvnMergeInfoCacheListener {
  void copyRevisionUpdated();
}
