package org.jetbrains.idea.svn.difftool;

import consulo.application.dumb.DumbAware;
import consulo.dataContext.DataManager;
import consulo.dataContext.DataProvider;
import consulo.diff.content.DiffContent;
import consulo.diff.content.EmptyContent;
import consulo.diff.request.DiffRequest;
import consulo.ide.impl.dataContext.BaseDataManager;
import consulo.ide.impl.idea.diff.DiffContext;
import consulo.ide.impl.idea.diff.FrameDiffTool.DiffViewer;
import consulo.ide.impl.idea.diff.FrameDiffTool.ToolbarComponents;
import consulo.ide.impl.idea.diff.requests.ErrorDiffRequest;
import consulo.ide.impl.idea.diff.tools.ErrorDiffTool;
import consulo.ide.impl.idea.diff.util.DiffUtil;
import consulo.ide.impl.idea.openapi.util.Disposer;
import consulo.ide.impl.idea.ui.EditorNotificationPanel;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.ex.action.util.ActionUtil;
import consulo.ui.ex.awt.*;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.difftool.SvnDiffSettingsHolder.SvnDiffSettings;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffRequest;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffViewer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.jetbrains.idea.svn.properties.PropertyValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;
import java.util.*;

public class SvnDiffViewer implements DiffViewer {
  private static final Logger LOG = Logger.getInstance(SvnDiffViewer.class);

  public static final Key<MyPropertyContext> PROPERTY_CONTEXT_KEY = Key.create("MyPropertyContext");
  public static final Key<Boolean> FOCUSED_VIEWER_KEY = Key.create("SvnFocusedViewer");

  @Nullable
  private final Project myProject;

  @Nonnull
  private final DiffContext myContext;
  @Nonnull
  private final DiffRequest myPropertyRequest;

  @Nonnull
  private final SvnDiffSettings mySettings;

  @Nonnull
  private final JPanel myPanel;
  @Nonnull
  private final Splitter mySplitter;
  @Nonnull
  private final Wrapper myNotificationPanel;

  @Nonnull
  private final DiffViewer myContentViewer;
  @Nonnull
  private final DiffViewer myPropertiesViewer;

  @Nonnull
  private final FocusListener myContentFocusListener = new MyFocusListener(false);
  @Nonnull
  private final FocusListener myPropertiesFocusListener = new MyFocusListener(true);

  private boolean myPropertiesViewerFocused; // False - content viewer, True - properties
  private boolean myDumbContentViewer;

  public SvnDiffViewer(@Nonnull DiffContext context, @Nonnull DiffRequest propertyRequest, @Nonnull DiffViewer wrappingViewer) {
    myProject = context.getProject();
    myContext = context;
    myPropertyRequest = propertyRequest;
    myContentViewer = wrappingViewer;

    myPropertyRequest.onAssigned(true);

    mySettings = initSettings(context);

    mySplitter = new MySplitter("Property Changes");
    mySplitter.setProportion(mySettings.getSplitterProportion());
    mySplitter.setFirstComponent(myContentViewer.getComponent());

    myNotificationPanel = new Wrapper();

    MyPropertyContext propertyContext = initPropertyContext(context);
    myPropertiesViewer = createPropertiesViewer(propertyRequest, propertyContext);

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(mySplitter, BorderLayout.CENTER);
    myPanel.add(myNotificationPanel, BorderLayout.SOUTH);
    DataManager.registerDataProvider(myPanel, new DataProvider() {
      @Override
      public Object getData(@NonNls Key<?> dataId) {
        DataProvider propertiesDataProvider = ((BaseDataManager)DataManager.getInstance()).getDataProviderEx(myPropertiesViewer.getComponent());
        DataProvider contentDataProvider = ((BaseDataManager) DataManager.getInstance()).getDataProviderEx(myContentViewer.getComponent());
        DataProvider defaultDP = myPropertiesViewerFocused ? propertiesDataProvider : contentDataProvider;
        DataProvider fallbackDP = myPropertiesViewerFocused ? contentDataProvider : propertiesDataProvider;
        return DiffUtil.getData(defaultDP, fallbackDP, dataId);
      }
    });

    updatePropertiesPanel();
  }

  @Nonnull
  private static DiffViewer createPropertiesViewer(@Nonnull DiffRequest propertyRequest, @Nonnull MyPropertyContext propertyContext) {
    if (propertyRequest instanceof SvnPropertiesDiffRequest) {
      return SvnPropertiesDiffViewer.create(propertyContext, ((SvnPropertiesDiffRequest)propertyRequest), true);
    }
    else {
      return ErrorDiffTool.INSTANCE.createComponent(propertyContext, propertyRequest);
    }
  }

  @Nonnull
  @Override
  public ToolbarComponents init() {
    installListeners();

    processContextHints();

    ToolbarComponents properties = myPropertiesViewer.init();
    ToolbarComponents components = new ToolbarComponents();
    components.toolbarActions = createToolbar(properties.toolbarActions);
    return components;
  }

  @Override
  public void dispose() {
    destroyListeners();

    updateContextHints();

    Disposer.dispose(myPropertiesViewer);

    myPropertyRequest.onAssigned(false);
  }

  private void processContextHints() {
    if (myContext.getUserData(FOCUSED_VIEWER_KEY) == Boolean.TRUE) myPropertiesViewerFocused = true;
    myDumbContentViewer = myContentViewer.getPreferredFocusedComponent() == null;
  }

  private void updateContextHints() {
    if (!myDumbContentViewer && !mySettings.isHideProperties()) myContext.putUserData(FOCUSED_VIEWER_KEY, myPropertiesViewerFocused);
    mySettings.setSplitterProportion(mySplitter.getProportion());
  }

  //
  // Diff
  //

  @Nullable
  private JComponent createNotification() {
    if (myPropertyRequest instanceof ErrorDiffRequest) {
      return createNotification(((ErrorDiffRequest)myPropertyRequest).getMessage());
    }

    List<DiffContent> contents = ((SvnPropertiesDiffRequest)myPropertyRequest).getContents();

    Map<String, PropertyValue> before = getProperties(contents.get(0));
    Map<String, PropertyValue> after = getProperties(contents.get(1));

    if (before.isEmpty() && after.isEmpty()) return null;

    if (!before.keySet().equals(after.keySet())) {
      return createNotification("SVN Properties changed");
    }

    for (String key : before.keySet()) {
      if (!Comparing.equal(before.get(key), after.get(key))) return createNotification("SVN Properties changed");
    }

    return null;
  }

  @Nonnull
  private static Map<String, PropertyValue> getProperties(@Nonnull DiffContent content) {
    if (content instanceof EmptyContent) return Collections.emptyMap();

    List<PropertyData> properties = ((SvnPropertiesDiffRequest.PropertyContent)content).getProperties();

    Map<String, PropertyValue> map = new HashMap<>();

    for (PropertyData data : properties) {
      if (map.containsKey(data.getName())) LOG.warn("Duplicated property: " + data.getName());
      map.put(data.getName(), data.getValue());
    }

    return map;
  }

  @Nonnull
  private static JPanel createNotification(@Nonnull String text) {
    return new EditorNotificationPanel().text(text);
  }

  //
  // Misc
  //

  private void updatePropertiesPanel() {
    boolean wasFocused = myContext.isFocused();
    if (!mySettings.isHideProperties()) {
      mySplitter.setSecondComponent(myPropertiesViewer.getComponent());
      myNotificationPanel.setContent(null);
    }
    else {
      mySplitter.setSecondComponent(null);
      myNotificationPanel.setContent(createNotification());
    }
    if (wasFocused) myContext.requestFocus();
  }

  @Nonnull
  private List<AnAction> createToolbar(@Nullable List<AnAction> propertiesActions) {
    List<AnAction> result = new ArrayList<>();

    if (propertiesActions != null) result.addAll(propertiesActions);

    result.add(new ToggleHidePropertiesAction());

    return result;
  }

  @Nonnull
  private static SvnDiffSettings initSettings(@Nonnull DiffContext context) {
    SvnDiffSettings settings = context.getUserData(SvnDiffSettingsHolder.KEY);
    if (settings == null) {
      settings = SvnDiffSettings.getSettings();
      context.putUserData(SvnDiffSettingsHolder.KEY, settings);
    }
    return settings;
  }

  @Nonnull
  private MyPropertyContext initPropertyContext(@Nonnull DiffContext context) {
    MyPropertyContext propertyContext = context.getUserData(PROPERTY_CONTEXT_KEY);
    if (propertyContext == null) {
      propertyContext = new MyPropertyContext();
      context.putUserData(PROPERTY_CONTEXT_KEY, propertyContext);
    }
    return propertyContext;
  }

  private void installListeners() {
    myContentViewer.getComponent().addFocusListener(myContentFocusListener);
    myPropertiesViewer.getComponent().addFocusListener(myPropertiesFocusListener);
  }

  private void destroyListeners() {
    myContentViewer.getComponent().removeFocusListener(myContentFocusListener);
    myPropertiesViewer.getComponent().removeFocusListener(myPropertiesFocusListener);
  }

  //
  // Getters
  //

  @Nonnull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    if (myPropertiesViewerFocused) {
      JComponent component = getPropertiesPreferredFocusedComponent();
      if (component != null) return component;
      return myContentViewer.getPreferredFocusedComponent();
    }
    else {
      JComponent component = myContentViewer.getPreferredFocusedComponent();
      if (component != null) return component;
      return getPropertiesPreferredFocusedComponent();
    }
  }

  @Nullable
  private JComponent getPropertiesPreferredFocusedComponent() {
    if (mySettings.isHideProperties()) return null;
    return myPropertiesViewer.getPreferredFocusedComponent();
  }

  //
  // Actions
  //

  private class ToggleHidePropertiesAction extends ToggleAction implements DumbAware {
    public ToggleHidePropertiesAction() {
      ActionUtil.copyFrom(this, "Subversion.TogglePropertiesDiff");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return !mySettings.isHideProperties();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySettings.setHideProperties(!state);
      updatePropertiesPanel();
    }
  }

  //
  // Helpers
  //

  private class MyPropertyContext extends DiffContext
  {
    @Nullable
    @Override
    public Project getProject() {
      return myContext.getProject();
    }

    @Override
    public boolean isWindowFocused() {
      return myContext.isWindowFocused();
    }

    @Override
    public boolean isFocused() {
      return DiffUtil.isFocusedComponent(getProject(), myPropertiesViewer.getComponent());
    }

    @Override
    public void requestFocus() {
      DiffUtil.requestFocus(getProject(), myPropertiesViewer.getPreferredFocusedComponent());
    }
  }

  private class MyFocusListener extends FocusAdapter {
    private final boolean myValue;

    public MyFocusListener(boolean value) {
      myValue = value;
    }

    @Override
    public void focusGained(FocusEvent e) {
      myPropertiesViewerFocused = myValue;
    }
  }

  private static class MySplitter extends Splitter
  {
    @Nonnull
	private final String myLabelText;

    public MySplitter(@Nonnull String text) {
      super(true);
      myLabelText = text;
    }

    @Override
    protected Divider createDivider() {
      return new DividerImpl() {
        @Override
        public void setOrientation(boolean isVerticalSplit) {
          if (!isVertical()) LOG.warn("unsupported state: splitter should be vertical");

          removeAll();

          setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));

          JLabel label = new JLabel(myLabelText);
          label.setFont(UIUtil.getOptionPaneMessageFont());
          label.setForeground(UIUtil.getLabelForeground());
          add(label, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBUI.insets(2), 0, 0));
          setDividerWidth(label.getPreferredSize().height + JBUI.scale(4));

          revalidate();
          repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          g.setColor(JBColor.border());
          UIUtil.drawLine(g, 0, 0, getWidth(), 0);
        }
      };
    }
  }
}
