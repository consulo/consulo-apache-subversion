package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DiffContentBase;
import com.intellij.diff.contents.EmptyContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.util.containers.ContainerUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.properties.PropertyData;

import java.util.List;

public class SvnPropertiesDiffRequest extends ContentDiffRequest {
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
