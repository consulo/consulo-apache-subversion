package org.jetbrains.idea.svn;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;

/**
 * @author VISTALL
 * @since 01/06/2023
 */
@TopicAPI(ComponentScope.APPLICATION)
public interface SvnWcConverted {
  void wcConverted();
}
