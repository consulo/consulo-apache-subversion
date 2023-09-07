package org.jetbrains.idea.svn.history;

import consulo.versionControlSystem.VcsException;

import javax.annotation.Nullable;

import consulo.versionControlSystem.change.ContentRevision;
import org.jetbrains.idea.svn.properties.PropertyData;

import java.util.List;

public interface PropertyRevision extends ContentRevision
{
  @Nullable
  List<PropertyData> getProperties() throws VcsException;
}