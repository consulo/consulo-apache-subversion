package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.properties.PropertyData;

import java.util.List;

public interface PropertyRevision extends ContentRevision {
  @Nullable
  List<PropertyData> getProperties() throws VcsException;
}