package org.jetbrains.idea.svn.difftool.properties;

import consulo.application.progress.ProgressIndicator;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorSettings;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.markup.SeparatorPlacement;
import consulo.diff.DiffContentFactory;
import consulo.diff.DiffUserDataKeys;
import consulo.diff.comparison.ComparisonManager;
import consulo.diff.comparison.ComparisonPolicy;
import consulo.diff.comparison.DiffTooBigException;
import consulo.diff.content.DiffContent;
import consulo.diff.content.DocumentContent;
import consulo.diff.fragment.DiffFragment;
import consulo.diff.fragment.LineFragment;
import consulo.diff.request.ContentDiffRequest;
import consulo.diff.util.Side;
import consulo.document.Document;
import consulo.document.event.DocumentEvent;
import consulo.document.impl.DocumentImpl;
import consulo.ide.impl.idea.diff.DiffContext;
import consulo.ide.impl.idea.diff.fragments.LineFragmentImpl;
import consulo.ide.impl.idea.diff.tools.util.DiffSplitter;
import consulo.ide.impl.idea.diff.tools.util.SyncScrollSupport;
import consulo.ide.impl.idea.diff.tools.util.side.TwosideTextDiffViewer;
import consulo.ide.impl.idea.diff.util.DiffDividerDrawUtil;
import consulo.ide.impl.idea.diff.util.DiffDrawUtil;
import consulo.ide.impl.idea.diff.util.DiffUtil;
import consulo.ide.impl.idea.diff.util.TextDiffType;
import consulo.language.editor.PlatformDataKeys;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.style.StandardColors;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.EmptyRunnable;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.status.FileStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.jetbrains.idea.svn.properties.PropertyValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.*;

public class SvnPropertiesDiffViewer extends TwosideTextDiffViewer {
  @Nonnull
  private final WrapperRequest myWrapperRequest;
  @Nonnull
  private final List<DiffChange> myDiffChanges;

  private boolean myFirstRediff = true;

  @Nonnull
  public static SvnPropertiesDiffViewer create(@Nonnull DiffContext context,
                                               @Nonnull SvnPropertiesDiffRequest request) {
    return create(context, request, false);
  }

  @Nonnull
  public static SvnPropertiesDiffViewer create(@Nonnull DiffContext context,
                                               @Nonnull SvnPropertiesDiffRequest request,
                                               boolean embedded) {
    Pair<WrapperRequest, List<DiffChange>> pair = convertRequest(request, embedded);
    return new SvnPropertiesDiffViewer(context, pair.first, pair.second);
  }

  private SvnPropertiesDiffViewer(@Nonnull DiffContext context,
                                  @Nonnull WrapperRequest request,
                                  @Nonnull List<DiffChange> diffChanges) {
    super(context, request);
    myWrapperRequest = request;
    myDiffChanges = diffChanges;

    for (EditorEx editor : getEditors()) {
      if (editor == null) continue;
      EditorSettings settings = editor.getSettings();

      settings.setAdditionalLinesCount(0);
      settings.setAdditionalColumnsCount(1);
      settings.setRightMarginShown(false);
      settings.setFoldingOutlineShown(false);
      settings.setLineNumbersShown(false);
      settings.setLineMarkerAreaShown(false);
      settings.setIndentGuidesShown(false);
      settings.setVirtualSpace(false);
      settings.setWheelFontChangeEnabled(false);
      settings.setAdditionalPageAtBottom(false);
      settings.setCaretRowShown(false);
      settings.setUseSoftWraps(false);
      editor.reinitSettings();
    }

    for (DiffChange change : myDiffChanges) {
      DiffDrawUtil.createBorderLineMarker(getEditor(Side.LEFT),
                                                                          change.myEndLine1,
                                                                          SeparatorPlacement.TOP);
      DiffDrawUtil.createBorderLineMarker(getEditor(Side.RIGHT),
                                                                          change.myEndLine2,
                                                                          SeparatorPlacement.TOP);
    }

    DiffSplitter splitter = myContentPanel.getSplitter();
    splitter.setDividerWidth(120);
    splitter.setShowDividerIcon(false);
  }

  @Override
  protected void onInit() {
    super.onInit();
    myContentPanel.setPainter(new MyDividerPainter());
  }

  @Nonnull
  @Override
  protected Runnable performRediff(@Nonnull ProgressIndicator indicator) {
    if (!myFirstRediff) return new EmptyRunnable();
    myFirstRediff = false;

    for (DiffChange change : myDiffChanges) {
      PropertyRecord record = change.getRecord();
      String before = record.getBefore();
      String after = record.getAfter();

      assert before != null || after != null;
      if (before == null) {
        change.setFragments(createEverythingChanged(0, after.length(), 0, StringUtil.countNewLines(after) + 1));
        continue;
      }
      if (after == null) {
        change.setFragments(createEverythingChanged(before.length(), 0, StringUtil.countNewLines(before) + 1, 0));
        continue;
      }

      try {
        ComparisonManager manager = ComparisonManager.getInstance();
        change.setFragments(manager.squash(manager.compareLinesInner(before, after, ComparisonPolicy.DEFAULT, indicator)));
      }
      catch (DiffTooBigException e) {
        change.setFragments(createEverythingChanged(before.length(), after.length(),
                                                    StringUtil.countNewLines(before) + 1, StringUtil.countNewLines(after) + 1));
      }
    }

    return new Runnable() {
      @Override
      public void run() {
        for (DiffChange change : myDiffChanges) {
          setupHighlighting(change, Side.LEFT);
          setupHighlighting(change, Side.RIGHT);
        }
      }
    };
  }

  private void setupHighlighting(@Nonnull DiffChange change, @Nonnull Side side) {
    PropertyRecord record = change.getRecord();
    List<? extends LineFragment> fragments = change.getFragments();
    assert fragments != null;

    EditorEx editor = getEditor(side);
    Document document = editor.getDocument();
    int changeStartLine = change.getStartLine(side);

    for (LineFragment fragment : fragments) {
      List<DiffFragment> innerFragments = fragment.getInnerFragments();

      int startLine = side.getStartLine(fragment) + changeStartLine;
      int endLine = side.getEndLine(fragment) + changeStartLine;

      int start = document.getLineStartOffset(startLine);
      TextDiffType type = DiffUtil.getLineDiffType(fragment);

      DiffDrawUtil.createHighlighter(editor, startLine, endLine, type, innerFragments != null);

      // TODO: we can paint LineMarker here, but it looks ugly for small editors
      if (innerFragments != null) {
        for (DiffFragment innerFragment : innerFragments) {
          int innerStart = side.getStartOffset(innerFragment);
          int innerEnd = side.getEndOffset(innerFragment);
          TextDiffType innerType = DiffUtil.getDiffType(innerFragment);

          innerStart += start;
          innerEnd += start;

          DiffDrawUtil.createInlineHighlighter(editor, innerStart, innerEnd, innerType);
        }
      }
    }
  }

  @Nonnull
  private static List<? extends LineFragment> createEverythingChanged(int length1, int length2, int lines1, int lines2) {
    return Collections.singletonList(new LineFragmentImpl(0,
                                                                                               lines1,
                                                                                               0,
                                                                                               lines2,
                                                                                               0,
                                                                                               length1,
                                                                                               0,
                                                                                               length2));
  }

  private class MyDividerPainter implements DiffSplitter.Painter, DiffDividerDrawUtil.DividerPaintable {
    @Nonnull
    private final JBLabel myLabel;

    public MyDividerPainter() {
      myLabel = new JBLabel();
      myLabel.setFont(UIUtil.getLabelFont());
      myLabel.setHorizontalAlignment(SwingConstants.CENTER);
      myLabel.setVerticalAlignment(SwingConstants.TOP);
    }

    @Override
    public void paint(@Nonnull Graphics g, @Nonnull JComponent divider) {
      Graphics2D gg = DiffDividerDrawUtil.getDividerGraphics(g, divider, getEditor1().getComponent());
      Rectangle clip = gg.getClipBounds();
      if (clip == null) return;

      gg.setColor(TargetAWT.to(DiffDrawUtil.getDividerColor()));
      gg.fill(clip);

      EditorEx editor1 = getEditor1();
      EditorEx editor2 = getEditor2();

      JComponent header1 = editor1.getHeaderComponent();
      JComponent header2 = editor2.getHeaderComponent();
      int headerOffset1 = header1 == null ? 0 : header1.getHeight();
      int headerOffset2 = header2 == null ? 0 : header2.getHeight();

      // TODO: painting is ugly if shift1 != shift2 (ex: search field is opened for one of editors)
      int shift1 = editor1.getScrollingModel().getVerticalScrollOffset() - headerOffset1;
      int shift2 = editor2.getScrollingModel().getVerticalScrollOffset() - headerOffset2;
      double rotate = shift1 == shift2 ? 0 : Math.atan2(shift2 - shift1, clip.width);

      DiffDividerDrawUtil.paintPolygons(gg, divider.getWidth(), false, rotate == 0, editor1, editor2, this);

      for (DiffChange change : myDiffChanges) {
        int y1 = editor1.logicalPositionToXY(new LogicalPosition(change.getStartLine(Side.LEFT), 0)).y - shift1;
        int y2 = editor2.logicalPositionToXY(new LogicalPosition(change.getStartLine(Side.RIGHT),
                                                                                    0)).y - shift2;
        int endY1 = editor1.logicalPositionToXY(new LogicalPosition(change.getEndLine(Side.LEFT), 0)).y - shift1;
        int endY2 = editor2.logicalPositionToXY(new LogicalPosition(change.getEndLine(Side.RIGHT), 0)).y - shift2;

        AffineTransform oldTransform = gg.getTransform();
        gg.translate(0, y1);
        if (rotate != 0) gg.rotate(-rotate);
        myLabel.setText(change.getRecord().getName());
        myLabel.setForeground(TargetAWT.to(getRecordTitleColor(change)));
        myLabel.setBounds(clip);
        myLabel.paint(gg);
        gg.setTransform(oldTransform);

        gg.setColor(JBColor.border());
        gg.drawLine(0, y1 - 1, clip.width, y2 - 1);
        gg.drawLine(0, endY1 - 1, clip.width, endY2 - 1);
      }
      gg.dispose();
    }

    @Override
    public void process(@Nonnull Handler handler) {
      for (DiffChange diffChange : myDiffChanges) {
        TextDiffType type = getDiffType(diffChange);
        if (type == null) continue;
        int shift1 = diffChange.getStartLine(Side.LEFT);
        int shift2 = diffChange.getStartLine(Side.RIGHT);

        List<? extends LineFragment> fragments = diffChange.getFragments();
        if (fragments == null) continue;

        for (LineFragment fragment : diffChange.getFragments()) {
          if (!handler.process(Side.LEFT.getStartLine(fragment) + shift1,
                               Side.LEFT.getEndLine(fragment) + shift1,
                               Side.RIGHT.getStartLine(fragment) + shift2,
                               Side.RIGHT.getEndLine(fragment) + shift2,
                               DiffUtil.getLineDiffType(fragment).getColor(getEditor1()))) {
            return;
          }
        }
      }
    }

    @Nullable
    private ColorValue getRecordTitleColor(@Nonnull DiffChange change) {
      TextDiffType type = getDiffType(change);
      if (type == TextDiffType.INSERTED) return FileStatus.ADDED.getColor();
      if (type == TextDiffType.DELETED)
        return FileStatus.DELETED.getColor();
      if (type == TextDiffType.MODIFIED)
        return FileStatus.MODIFIED.getColor();
      return StandardColors.BLACK; // unchanged
    }

    @Nullable
    public TextDiffType getDiffType(@Nonnull DiffChange change) {
      if (change.getRecord().getBefore() == null) return TextDiffType.INSERTED;
      if (change.getRecord().getAfter() == null) return TextDiffType.DELETED;
      if (change.getFragments() != null && !change.getFragments().isEmpty()) return TextDiffType.MODIFIED;
      return null; // unchanged
    }
  }

  @Override
  protected void onDocumentChange(@Nonnull DocumentEvent event) {
    LOG.warn("Document changes are not supported");
  }

  //
  // Impl
  //


  @Nullable
  @Override
  protected SyncScrollSupport.SyncScrollable getSyncScrollable() {
    return new SyncScrollSupport.SyncScrollable() {
      @Override
      public boolean isSyncScrollEnabled() {
        return true;
      }

      @Override
      public int transfer(@Nonnull Side side, int line) {
        return line;
      }
    };
  }

  @Nullable
  @Override
  public Object getData(@NonNls Key<?> dataId) {
    if (PlatformDataKeys.HELP_ID == dataId) {
      return "topicId758145";
    }
    return super.getData(dataId);
  }

  //
  // Initial step
  //

  @Nonnull
  private static Pair<WrapperRequest, List<DiffChange>> convertRequest(@Nonnull SvnPropertiesDiffRequest request,
                                                                                         boolean embedded) {
    List<PropertyRecord> records = collectRecords(request);

    StringBuilder builder1 = new StringBuilder();
    StringBuilder builder2 = new StringBuilder();
    List<DiffChange> diffChanges = new ArrayList<>();

    int totalLines = 0;
    for (PropertyRecord record : records) {
      int start = totalLines;

      String before = StringUtil.notNullize(record.getBefore());
      String after = StringUtil.notNullize(record.getAfter());
      builder1.append(before);
      builder2.append(after);

      int lines1 = StringUtil.countNewLines(before);
      int lines2 = StringUtil.countNewLines(after);

      int appendedLines = Math.max(lines1, lines2) + 1;
      totalLines += appendedLines;

      for (int i = lines1; i < appendedLines; i++) {
        builder1.append('\n');
      }
      for (int i = lines2; i < appendedLines; i++) {
        builder2.append('\n');
      }

      diffChanges.add(new DiffChange(record, start, totalLines, start, totalLines));
    }

    Document document1 = new DocumentImpl(builder1);
    Document document2 = new DocumentImpl(builder2);

    return Pair.create(new WrapperRequest(request, document1, document2, embedded), diffChanges);
  }

  @Nonnull
  private static List<PropertyRecord> collectRecords(@Nonnull SvnPropertiesDiffRequest request) {
    List<DiffContent> originalContents = request.getContents();
    List<PropertyData> properties1 = getProperties(originalContents.get(0));
    List<PropertyData> properties2 = getProperties(originalContents.get(1));

    Map<String, PropertyValue> before = new HashMap<>();
    Map<String, PropertyValue> after = new HashMap<>();
    if (properties1 != null) {
      for (PropertyData data : properties1) {
        before.put(data.getName(), data.getValue());
      }
    }
    if (properties2 != null) {
      for (PropertyData data : properties2) {
        after.put(data.getName(), data.getValue());
      }
    }

    List<PropertyRecord> records = new ArrayList<>();
    for (String name : ContainerUtil.union(before.keySet(), after.keySet())) {
      records.add(createRecord(name, before.get(name), after.get(name)));
    }

    ContainerUtil.sort(records, new Comparator<PropertyRecord>() {
      @Override
      public int compare(PropertyRecord o1, PropertyRecord o2) {
        return StringUtil.naturalCompare(o1.getName(), o2.getName());
      }
    });

    return records;
  }

  @Nullable
  private static List<PropertyData> getProperties(@Nonnull DiffContent content) {
    if (content instanceof SvnPropertiesDiffRequest.PropertyContent) {
      return ((SvnPropertiesDiffRequest.PropertyContent)content).getProperties();
    }
    return null;
  }

  @Nullable
  private static PropertyRecord createRecord(@Nonnull String name, @Nullable PropertyValue value1, @Nullable PropertyValue value2) {
    assert value1 != null || value2 != null;

    String text1 = value1 != null ? value1.toString() : null;
    String text2 = value2 != null ? value2.toString() : null;

    // TODO: show differences in line separators ?
    if (text1 != null) text1 = StringUtil.convertLineSeparators(text1);
    if (text2 != null) text2 = StringUtil.convertLineSeparators(text2);

    return new PropertyRecord(name, text1, text2);
  }

  //
  // Helpers
  //

  private static class WrapperRequest extends ContentDiffRequest {
    @Nonnull
    SvnPropertiesDiffRequest myRequest;
    @Nonnull
    DocumentContent myContent1;
    @Nonnull
    DocumentContent myContent2;
    private final boolean myEmbedded;

    public WrapperRequest(@Nonnull SvnPropertiesDiffRequest request,
                          @Nonnull Document document1,
                          @Nonnull Document document2,
                          boolean embedded) {
      myRequest = request;
      myContent1 = DiffContentFactory.getInstance().create(null, document1);
      myContent2 = DiffContentFactory.getInstance().create(null, document2);
      myEmbedded = embedded;

      putUserData(DiffUserDataKeys.FORCE_READ_ONLY, true);
    }

    @Nonnull
    public SvnPropertiesDiffRequest getPropertiesRequest() {
      return myRequest;
    }

    @Nonnull
    @Override
    public List<DiffContent> getContents() {
      return ContainerUtil.<DiffContent>list(myContent1, myContent2);
    }

    @Nonnull
    @Override
    public List<String> getContentTitles() {
      return myEmbedded ? ContainerUtil.<String>list(null, null) : myRequest.getContentTitles();
    }

    @Nullable
    @Override
    public String getTitle() {
      return myRequest.getTitle();
    }

    @Override
    public <T> T getUserData(@Nonnull Key<T> key) {
      return myRequest.getUserData(key);
    }

    @Override
    public <T> void putUserData(@Nonnull Key<T> key, @Nullable T value) {
      myRequest.putUserData(key, value);
    }
  }

  private static class PropertyRecord {
    @Nonnull
    private final String myName;
    @Nullable
    private final String myBefore;
    @Nullable
    private final String myAfter;

    public PropertyRecord(@Nonnull String name,
                          @Nullable String before,
                          @Nullable String after) {
      assert before != null || after != null;

      myName = name;
      myBefore = before;
      myAfter = after;
    }

    @Nonnull
    public String getName() {
      return myName;
    }

    @Nullable
    public String getBefore() {
      return myBefore;
    }

    @Nullable
    public String getAfter() {
      return myAfter;
    }
  }

  private static class DiffChange {
    @Nonnull
    private final PropertyRecord myRecord;
    private final int myStartLine1;
    private final int myEndLine1;
    private final int myStartLine2;
    private final int myEndLine2;

    @Nullable
    private List<? extends LineFragment> myFragments;

    public DiffChange(@Nonnull PropertyRecord record, int startLine1, int endLine1, int startLine2, int endLine2) {
      myRecord = record;
      myStartLine1 = startLine1;
      myEndLine1 = endLine1;
      myStartLine2 = startLine2;
      myEndLine2 = endLine2;
    }

    @Nonnull
    public PropertyRecord getRecord() {
      return myRecord;
    }

    public int getStartLine(@Nonnull Side side) {
      return side.select(myStartLine1, myStartLine2);
    }

    public int getEndLine(@Nonnull Side side) {
      return side.select(myEndLine1, myEndLine2);
    }

    public void setFragments(@Nullable List<? extends LineFragment> fragments) {
      myFragments = fragments;
    }

    @Nullable
    public List<? extends LineFragment> getFragments() {
      return myFragments;
    }
  }
}
