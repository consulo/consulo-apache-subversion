package org.jetbrains.idea.svn.difftool.properties;

import consulo.diff.content.DiffContent;
import consulo.diff.content.DiffContentBase;
import consulo.diff.content.EmptyContent;
import consulo.diff.request.ContentDiffRequest;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.fileType.FileType;
import org.jetbrains.idea.svn.properties.PropertyData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class SvnPropertiesDiffRequest extends ContentDiffRequest
{
  @Nonnull
  private final List<DiffContent> myContents;
  @Nonnull
  private final List<String> myContentTitles;
  @Nonnull
  private final String myWindowTitle;

  public SvnPropertiesDiffRequest(@Nonnull String windowTitle,
                                  @Nonnull DiffContent content1,
                                  @Nonnull DiffContent content2,
                                  @Nonnull String title1,
                                  @Nonnull String title2) {
    myWindowTitle = windowTitle;
    myContents = ContainerUtil.list(content1, content2);
    myContentTitles = ContainerUtil.list(title1, title2);

    assert content1 instanceof PropertyContent || content1 instanceof EmptyContent;
    assert content2 instanceof PropertyContent || content2 instanceof EmptyContent;
    assert content1 instanceof PropertyContent || content2 instanceof PropertyContent;
  }

  public SvnPropertiesDiffRequest(@Nullable List<PropertyData> before, @Nullable List<PropertyData> after,
                                  @Nullable String title1, @Nullable String title2) {
    assert before != null || after != null;

    myContents = ContainerUtil.list(createContent(before), createContent(after));
    myWindowTitle = "Svn Properties Diff";
    myContentTitles = ContainerUtil.list(title1, title2);
  }

  @Nonnull
  public DiffContent createContent(@Nullable List<PropertyData> content) {
    if (content == null) return new EmptyContent();

    return new PropertyContent(content);
  }

  @Nonnull
  @Override
  public String getTitle() {
    return myWindowTitle;
  }

  @Nonnull
  @Override
  public List<String> getContentTitles() {
    return myContentTitles;
  }

  @Nonnull
  @Override
  public List<DiffContent> getContents() {
    return myContents;
  }

  public static class PropertyContent extends DiffContentBase {
    @Nonnull
	private final List<PropertyData> myProperties;

    public PropertyContent(@Nonnull List<PropertyData> properties) {
      myProperties = properties;
    }

    @Nonnull
    public List<PropertyData> getProperties() {
      return myProperties;
    }

    @Nullable
    @Override
    public FileType getContentType() {
      return null;
    }
  }
}
