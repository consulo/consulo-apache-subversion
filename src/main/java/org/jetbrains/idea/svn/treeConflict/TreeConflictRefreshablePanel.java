/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.treeConflict;

import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.*;
import consulo.application.util.BackgroundTaskQueue;
import consulo.disposer.CompositeDisposable;
import consulo.disposer.Disposable;
import consulo.document.FileDocumentManager;
import consulo.ide.impl.idea.openapi.progress.impl.BackgroundableProcessIndicator;
import consulo.ide.impl.idea.openapi.util.Disposer;
import consulo.ide.impl.idea.openapi.vcs.history.FileHistoryPanelImpl;
import consulo.ide.impl.idea.openapi.vcs.history.FileHistoryRefresherI;
import consulo.ide.impl.idea.openapi.vcs.history.VcsAppendableHistoryPartnerAdapter;
import consulo.ide.impl.idea.ui.components.JBLoadingPanel;
import consulo.ide.impl.idea.util.BeforeAfter;
import consulo.ide.impl.idea.util.containers.Convertor;
import consulo.ide.impl.idea.util.ui.VcsBackgroundTask;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.primitive.longs.LongList;
import consulo.util.collection.primitive.longs.LongLists;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.history.VcsAbstractHistorySession;
import consulo.versionControlSystem.history.VcsFileRevision;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.ui.VcsBalloonProblemNotifier;
import consulo.versionControlSystem.util.VcsUtil;
import org.jetbrains.idea.svn.ConflictedSvnChange;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.conflict.ConflictAction;
import org.jetbrains.idea.svn.conflict.ConflictReason;
import org.jetbrains.idea.svn.conflict.ConflictVersion;
import org.jetbrains.idea.svn.conflict.TreeConflictDescription;
import org.jetbrains.idea.svn.history.SvnHistoryProvider;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

import static consulo.util.lang.ObjectUtil.notNull;
import static org.jetbrains.idea.svn.history.SvnHistorySession.getCurrentCommittedRevision;

public class TreeConflictRefreshablePanel implements Disposable {

  public static final String TITLE = "Resolve tree conflict";
  private final ConflictedSvnChange myChange;
  private final SvnVcs myVcs;
  private SvnRevisionNumber myCommittedRevision;
  private FilePath myPath;
  private final CompositeDisposable myChildDisposables = new CompositeDisposable();
  private final LongList myRightRevisionsList;
  @Nonnull
  private final String myLoadingTitle;
  @Nonnull
  private final JBLoadingPanel myDetailsPanel;
  @Nonnull
  private final BackgroundTaskQueue myQueue;
  private volatile ProgressIndicator myIndicator = new EmptyProgressIndicator();

  public TreeConflictRefreshablePanel(@Nonnull Project project,
                                      @Nonnull String loadingTitle,
                                      @Nonnull BackgroundTaskQueue queue,
                                      Change change) {
    myVcs = SvnVcs.getInstance(project);
    assert change instanceof ConflictedSvnChange;
    myChange = (ConflictedSvnChange)change;
    myPath = ChangesUtil.getFilePath(myChange);
    myRightRevisionsList = LongLists.newArrayList();

    myLoadingTitle = loadingTitle;
    myQueue = queue;
    myDetailsPanel = new JBLoadingPanel(new BorderLayout(), this);
  }

  public static boolean descriptionsEqual(TreeConflictDescription d1, TreeConflictDescription d2) {
    if (d1.isPropertyConflict() != d2.isPropertyConflict()) return false;
    if (d1.isTextConflict() != d2.isTextConflict()) return false;
    if (d1.isTreeConflict() != d2.isTreeConflict()) return false;

    if (!d1.getOperation().equals(d2.getOperation())) return false;
    if (!d1.getConflictAction().equals(d2.getConflictAction())) return false;
    if (!Comparing.equal(d1.getConflictReason(), d2.getConflictReason())) return false;
    if (!Comparing.equal(d1.getPath(), d2.getPath())) return false;
    if (!Comparing.equal(d1.getNodeKind(), d2.getNodeKind())) return false;
    if (!compareConflictVersion(d1.getSourceLeftVersion(), d2.getSourceLeftVersion())) return false;
    if (!compareConflictVersion(d1.getSourceRightVersion(), d2.getSourceRightVersion())) return false;
    return true;
  }

  private static boolean compareConflictVersion(ConflictVersion v1, ConflictVersion v2) {
    if (v1 == null && v2 == null) return true;
    if (v1 == null || v2 == null) return false;
    if (!v1.getKind().equals(v2.getKind())) return false;
    if (!v1.getPath().equals(v2.getPath())) return false;
    if (v1.getPegRevision() != v2.getPegRevision()) return false;
    if (!Comparing.equal(v1.getRepositoryRoot(), v2.getRepositoryRoot())) return false;
    return true;
  }

  @Nonnull
  public JPanel getPanel() {
    return myDetailsPanel;
  }

  private BeforeAfter<ConflictSidePresentation> processDescription(@Nonnull ProgressIndicator indicator,
                                                                   TreeConflictDescription description) throws VcsException {
    if (description == null) return null;
    if (myChange.getBeforeRevision() != null) {
      myCommittedRevision = (SvnRevisionNumber)getCurrentCommittedRevision(myVcs, myChange.getBeforeRevision() != null ? myChange
        .getBeforeRevision().getFile().getIOFile() : myPath.getIOFile());
    }

    indicator.checkCanceled();

    ConflictSidePresentation leftSide;
    ConflictSidePresentation rightSide;
    if (isDifferentURLs(description)) {
      leftSide = createSide(description.getSourceLeftVersion(), null, true);
      rightSide = createSide(description.getSourceRightVersion(), null, false);
    }
    else { //only one side
      leftSide = createSide(null, null, true);
      rightSide = createSide(description.getSourceRightVersion(), getPegRevisionFromLeftSide(description), false);
    }
    indicator.checkCanceled();
    leftSide.load();
    indicator.checkCanceled();
    rightSide.load();
    indicator.checkCanceled();

    return new BeforeAfter<>(leftSide, rightSide);
  }

  @Nullable
  private SVNRevision getPegRevisionFromLeftSide(@Nonnull TreeConflictDescription description) {
    SVNRevision result = null;
    if (description.getSourceLeftVersion() != null) {
      long committed = description.getSourceLeftVersion().getPegRevision();
      if (myCommittedRevision != null &&
        myCommittedRevision.getRevision().getNumber() < committed &&
        myCommittedRevision.getRevision().isValid()) {
        committed = myCommittedRevision.getRevision().getNumber();
      }
      result = SVNRevision.create(committed);
    }
    return result;
  }

  private static boolean isDifferentURLs(TreeConflictDescription description) {
    return description.getSourceLeftVersion() != null && description.getSourceRightVersion() != null &&
      !Comparing.equal(description.getSourceLeftVersion().getPath(), description.getSourceRightVersion().getPath());
  }

  @Nonnull
  private ConflictSidePresentation createSide(@Nullable ConflictVersion version, @Nullable SVNRevision untilThisOther, boolean isLeft)
    throws VcsException {
    ConflictSidePresentation result = EmptyConflictSide.getInstance();
    if (version != null &&
      (myChange.getBeforeRevision() == null ||
        myCommittedRevision == null ||
        !isLeft ||
        !myCommittedRevision.getRevision().isValid() ||
        myCommittedRevision.getRevision().getNumber() != version.getPegRevision())) {
      HistoryConflictSide side = new HistoryConflictSide(myVcs, version, untilThisOther);
      if (untilThisOther != null && !isLeft) {
        side.setListToReportLoaded(myRightRevisionsList);
      }
      result = side;
    }
    myChildDisposables.add(result);
    return result;
  }

  @RequiredUIAccess
  public void refresh() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myDetailsPanel.startLoading();
    Loader task = new Loader(myVcs.getProject(), myLoadingTitle);
    myIndicator = new BackgroundableProcessIndicator(task);
    myQueue.run(task, Application.get().getDefaultModalityState(), myIndicator);
  }

  @RequiredUIAccess
  protected JPanel dataToPresentation(BeforeAfter<BeforeAfter<ConflictSidePresentation>> data) {
    final JPanel wrapper = new JPanel(new BorderLayout());
    final JPanel main = new JPanel(new GridBagLayout());

    final GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                         JBUI.insets(1), 0, 0);
    final String pathComment = myCommittedRevision == null ? "" :
      " (current: " +
        myChange.getBeforeRevision().getRevisionNumber().asString() +
        ", committed: " +
        myCommittedRevision.asString() +
        ")";
    final JLabel name = new JLabel(myPath.getName() + pathComment);
    name.setFont(name.getFont().deriveFont(Font.BOLD));
    gb.insets.top = 5;
    main.add(name, gb);
    ++gb.gridy;
    gb.insets.top = 10;
    appendDescription(myChange.getBeforeDescription(), main, gb, data.getBefore(), myPath.isDirectory());
    appendDescription(myChange.getAfterDescription(), main, gb, data.getAfter(), myPath.isDirectory());
    wrapper.add(main, BorderLayout.NORTH);
    return wrapper;
  }

  private void appendDescription(TreeConflictDescription description,
                                 JPanel main,
                                 GridBagConstraints gb,
                                 BeforeAfter<ConflictSidePresentation> ba, boolean directory) {
    if (description == null) return;
    JLabel descriptionLbl = new JLabel(description.toPresentableString());
    descriptionLbl.setForeground(JBColor.RED);
    main.add(descriptionLbl, gb);
    ++gb.gridy;
    //buttons
    gb.insets.top = 0;
    addResolveButtons(description, main, gb);

    addSide(main, gb, ba.getBefore(), description.getSourceLeftVersion(), "Left", directory);
    addSide(main, gb, ba.getAfter(), description.getSourceRightVersion(), "Right", directory);
  }

  private void addResolveButtons(TreeConflictDescription description, JPanel main, GridBagConstraints gb) {
    final FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT, 5, 5);
    JPanel wrapper = new JPanel(flowLayout);
    final JButton both = new JButton("Both");
    final JButton merge = new JButton("Merge");
    final JButton left = new JButton("Accept Yours");
    final JButton right = new JButton("Accept Theirs");
    enableAndSetListener(createBoth(description), both);
    enableAndSetListener(createMerge(description), merge);
    enableAndSetListener(createLeft(description), left);
    enableAndSetListener(createRight(description), right);
    //wrapper.add(both);
    if (merge.isEnabled()) {
      wrapper.add(merge);
    }
    wrapper.add(left);
    wrapper.add(right);
    gb.insets.left = -4;
    main.add(wrapper, gb);
    gb.insets.left = 1;
    ++gb.gridy;
  }

  private ActionListener createRight(final TreeConflictDescription description) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int ok = Messages.showOkCancelDialog(myVcs.getProject(), "Accept theirs for " + filePath(myPath) + "?",
                                             TITLE, Messages.getQuestionIcon());
        if (Messages.OK != ok) return;
        FileDocumentManager.getInstance().saveAllDocuments();
        final Paths paths = getPaths(description);
        ProgressManager.getInstance().run(
          new VcsBackgroundTask<TreeConflictDescription>(myVcs.getProject(), "Accepting theirs for: " + filePath(paths.myMainPath),
                                                         PerformInBackgroundOption.ALWAYS_BACKGROUND,
                                                         Collections.singletonList(description),
                                                         true) {
            @Override
            protected void process(TreeConflictDescription d) throws VcsException {
              new SvnTreeConflictResolver(myVcs, paths.myMainPath, paths.myAdditionalPath).resolveSelectTheirsFull();
            }

            @Override
            public void onSuccess() {
              super.onSuccess();
              if (executedOk()) {
                VcsBalloonProblemNotifier.showOverChangesView((Project)myProject,
                                                              "Theirs accepted for " + filePath(paths.myMainPath),
                                                              NotificationType.INFORMATION);
              }
            }
          });
      }
    };
  }

  private Paths getPaths(final TreeConflictDescription description) {
    FilePath mainPath;
    FilePath additionalPath = null;
    if (myChange.isMoved() || myChange.isRenamed()) {
      if (ConflictAction.ADD.equals(description.getConflictAction())) {
        mainPath = myChange.getAfterRevision().getFile();
        additionalPath = myChange.getBeforeRevision().getFile();
      }
      else {
        mainPath = myChange.getBeforeRevision().getFile();
        additionalPath = myChange.getAfterRevision().getFile();
      }
    }
    else {
      mainPath = myChange.getBeforeRevision() != null ? myChange.getBeforeRevision().getFile() : myChange.getAfterRevision().getFile();
    }
    return new Paths(mainPath, additionalPath);
  }

  private static class Paths {
    public final FilePath myMainPath;
    public final FilePath myAdditionalPath;

    private Paths(FilePath mainPath, FilePath additionalPath) {
      myMainPath = mainPath;
      myAdditionalPath = additionalPath;
    }
  }

  private ActionListener createLeft(final TreeConflictDescription description) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int ok = Messages.showOkCancelDialog(myVcs.getProject(), "Accept yours for " + filePath(myPath) + "?",
                                             TITLE, Messages.getQuestionIcon());
        if (Messages.OK != ok) return;
        FileDocumentManager.getInstance().saveAllDocuments();
        final Paths paths = getPaths(description);
        ProgressManager.getInstance().run(
          new VcsBackgroundTask<TreeConflictDescription>(myVcs.getProject(),
                                                         "Accepting yours for: " + filePath(paths.myMainPath),
                                                         PerformInBackgroundOption.ALWAYS_BACKGROUND,
                                                         Collections.singletonList(description),
                                                         true) {
            @Override
            protected void process(TreeConflictDescription d) throws VcsException {
              new SvnTreeConflictResolver(myVcs, paths.myMainPath, paths.myAdditionalPath).resolveSelectMineFull();
            }

            @Override
            public void onSuccess() {
              super.onSuccess();
              if (executedOk()) {
                VcsBalloonProblemNotifier.showOverChangesView((Project)myProject,
                                                              "Yours accepted for " + filePath(paths.myMainPath),
                                                              NotificationType.INFORMATION);
              }
            }
          });
      }
    };
  }

  private ActionListener createMerge(final TreeConflictDescription description) {
    if (isDifferentURLs(description)) {
      return null;
    }
    // my edit, theirs move or delete
    if (ConflictAction.EDIT.equals(description.getConflictAction()) && description.getSourceLeftVersion() != null &&
      ConflictReason.DELETED.equals(description.getConflictReason()) && (myChange.isMoved() || myChange.isRenamed()) &&
      myCommittedRevision != null) {
      if (myPath.isDirectory() == description.getSourceRightVersion().isDirectory()) {
        return createMergeTheirsForFile(description);
      }
    }
    return null;
  }

  private ActionListener createMergeTheirsForFile(final TreeConflictDescription description) {
    return new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        new MergeFromTheirsResolver(myVcs, description, myChange, myCommittedRevision).execute();
      }
    };
  }

  @Nonnull
  public static String filePath(@Nonnull FilePath newFilePath) {
    return newFilePath.getName() + " (" + notNull(newFilePath.getParentPath()).getPath() + ")";
  }

  private static ActionListener createBoth(TreeConflictDescription description) {
    return null;
  }

  private static void enableAndSetListener(final ActionListener al, final JButton b) {
    if (al == null) {
      b.setEnabled(false);
    }
    else {
      b.addActionListener(al);
    }
  }

  private void addSide(JPanel main,
                       GridBagConstraints gb,
                       ConflictSidePresentation before,
                       ConflictVersion leftVersion, final String name, boolean directory) {
    final String leftPresentation = leftVersion == null ? name + ": (" + (directory ? "directory" : "file") +
      (myChange.getBeforeRevision() == null ? ") added" : ") unversioned") :
      name + ": " + FileUtil.toSystemIndependentName(ConflictVersion.toPresentableString(leftVersion));
    gb.insets.top = 10;
    main.add(new JLabel(leftPresentation), gb);
    ++gb.gridy;
    gb.insets.top = 0;

    if (before != null) {
      JPanel panel = before.createPanel();
      if (panel != null) {
        //gb.fill = GridBagConstraints.HORIZONTAL;
        main.add(panel, gb);
        //gb.fill = GridBagConstraints.NONE;
        ++gb.gridy;
      }
    }
  }

  @Override
  public void dispose() {
    myIndicator.cancel();
    Disposer.dispose(myChildDisposables);
  }

  private interface ConflictSidePresentation extends Disposable {
    JPanel createPanel();

    void load() throws VcsException;
  }

  private static class EmptyConflictSide implements ConflictSidePresentation {
    private static final EmptyConflictSide ourInstance = new EmptyConflictSide();

    public static EmptyConflictSide getInstance() {
      return ourInstance;
    }

    @Override
    public JPanel createPanel() {
      return null;
    }

    @Override
    public void dispose() {
    }

    @Override
    public void load() {
    }
  }

  private abstract static class AbstractConflictSide<T> implements ConflictSidePresentation, Convertor<T, VcsRevisionNumber> {
    protected final Project myProject;
    protected final ConflictVersion myVersion;

    private AbstractConflictSide(Project project, ConflictVersion version) {
      myProject = project;
      myVersion = version;
    }
  }

  private static class HistoryConflictSide extends AbstractConflictSide<VcsFileRevision> {
    public static final int LIMIT = 10;
    private final VcsAppendableHistoryPartnerAdapter mySessionAdapter;
    private final SvnHistoryProvider myProvider;
    private final FilePath myPath;
    private final SvnVcs myVcs;
    private final SVNRevision myPeg;
    private FileHistoryPanelImpl myFileHistoryPanel;
    private LongList myListToReportLoaded;

    private HistoryConflictSide(SvnVcs vcs, ConflictVersion version, final SVNRevision peg) throws VcsException {
      super(vcs.getProject(), version);
      myVcs = vcs;
      myPeg = peg;
      try {
        myPath = VcsUtil.getFilePathOnNonLocal(
          version.getRepositoryRoot().appendPath(FileUtil.toSystemIndependentName(version.getPath()), true).toString(),
          version.isDirectory());
      }
      catch (SVNException e) {
        throw new VcsException(e);
      }

      mySessionAdapter = new VcsAppendableHistoryPartnerAdapter();
      /*mySessionAdapter.reportCreatedEmptySession(new SvnHistorySession(myVcs, Collections.<VcsFileRevision>emptyList(),
        myPath, SvnUtil.checkRepositoryVersion15(myVcs, version.getPath()), null, true));*/
      myProvider = (SvnHistoryProvider)myVcs.getVcsHistoryProvider();
    }

    public void setListToReportLoaded(LongList listToReportLoaded) {
      myListToReportLoaded = listToReportLoaded;
    }

    @Override
    public VcsRevisionNumber convert(VcsFileRevision o) {
      return o.getRevisionNumber();
    }

    @Override
    public void load() throws VcsException {
      SVNRevision from = SVNRevision.create(myVersion.getPegRevision());
      myProvider.reportAppendableHistory(myPath, mySessionAdapter, from, myPeg, myPeg == null ? LIMIT : 0, myPeg, true);
      VcsAbstractHistorySession session = mySessionAdapter.getSession();
      if (myListToReportLoaded != null && session != null) {
        List<VcsFileRevision> list = session.getRevisionList();
        for (VcsFileRevision revision : list) {
          myListToReportLoaded.add(((SvnRevisionNumber)revision.getRevisionNumber()).getRevision().getNumber());
        }
      }
    }

    @Override
    public void dispose() {
      if (myFileHistoryPanel != null) {
        Disposer.dispose(myFileHistoryPanel);
      }
    }

    @Override
    public JPanel createPanel() {
      VcsAbstractHistorySession session = mySessionAdapter.getSession();
      if (session == null) return EmptyConflictSide.getInstance().createPanel();
      List<VcsFileRevision> list = session.getRevisionList();
      if (list.isEmpty()) {
        return EmptyConflictSide.getInstance().createPanel();
      }
      VcsFileRevision last = null;
      if (!list.isEmpty() && myPeg == null && list.size() == LIMIT ||
        myPeg != null && myPeg.getNumber() > 0 &&
          myPeg.equals(((SvnRevisionNumber)list.get(list.size() - 1).getRevisionNumber()).getRevision())) {
        last = list.remove(list.size() - 1);
      }
      myFileHistoryPanel = new FileHistoryPanelImpl(myVcs,
                                                    myPath,
                                                    session,
                                                    myProvider,
                                                    null,
                                                    new FileHistoryRefresherI() {
                                                      @Override
                                                      public void run(boolean isRefresh,
                                                                      boolean canUseCache) {
                                                        //we will not refresh
                                                      }

                                                      @Override
                                                      public boolean isFirstTime() {
                                                        return false;
                                                      }
                                                    },
                                                    true);
      myFileHistoryPanel.setBottomRevisionForShowDiff(last);
      myFileHistoryPanel.setBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()));
      return myFileHistoryPanel;
    }
  }

  private class Loader extends Task.Backgroundable {
    private BeforeAfter<BeforeAfter<ConflictSidePresentation>> myData;
    private VcsException myException;

    private Loader(@Nullable Project project, @Nonnull String title) {
      super(project, title, false);
    }

    @Override
    public void run(@Nonnull ProgressIndicator indicator) {
      try {
        myData = new BeforeAfter<>(processDescription(indicator, myChange.getBeforeDescription()),
                                   processDescription(indicator, myChange.getAfterDescription()));
      }
      catch (VcsException e) {
        myException = e;
      }
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        VcsBalloonProblemNotifier.showOverChangesView((Project)myProject, myException.getMessage(), NotificationType.ERROR);
      }
      else {
        myDetailsPanel.add(dataToPresentation(myData));
        myDetailsPanel.stopLoading();
      }
    }
  }
}
