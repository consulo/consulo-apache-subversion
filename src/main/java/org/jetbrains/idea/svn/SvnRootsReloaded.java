package org.jetbrains.idea.svn;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;

/**
 * @author VISTALL
 * @since 01/06/2023
 */
@TopicAPI(ComponentScope.PROJECT)
public interface SvnRootsReloaded {
  void rootsReloaded(boolean mappingChanged);
}
