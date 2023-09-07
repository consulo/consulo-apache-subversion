package org.jetbrains.idea.svn.integrate;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;

import java.util.List;

@TopicAPI(ComponentScope.PROJECT)
public interface CommittedChangesMergedStateChanged {
  void event(final List<CommittedChangeList> list);
}
