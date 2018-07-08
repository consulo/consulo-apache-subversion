package org.jetbrains.idea.svn.api;

import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.add.AddClient;
import org.jetbrains.idea.svn.annotate.AnnotateClient;
import org.jetbrains.idea.svn.browse.BrowseClient;
import org.jetbrains.idea.svn.change.ChangeListClient;
import org.jetbrains.idea.svn.checkin.CheckinClient;
import org.jetbrains.idea.svn.checkin.ImportClient;
import org.jetbrains.idea.svn.checkout.CheckoutClient;
import org.jetbrains.idea.svn.checkout.ExportClient;
import org.jetbrains.idea.svn.cleanup.CleanupClient;
import org.jetbrains.idea.svn.conflict.ConflictClient;
import org.jetbrains.idea.svn.content.ContentClient;
import org.jetbrains.idea.svn.copy.CopyMoveClient;
import org.jetbrains.idea.svn.delete.DeleteClient;
import org.jetbrains.idea.svn.diff.DiffClient;
import org.jetbrains.idea.svn.history.HistoryClient;
import org.jetbrains.idea.svn.info.InfoClient;
import org.jetbrains.idea.svn.integrate.MergeClient;
import org.jetbrains.idea.svn.lock.LockClient;
import org.jetbrains.idea.svn.properties.PropertyClient;
import org.jetbrains.idea.svn.revert.RevertClient;
import org.jetbrains.idea.svn.status.StatusClient;
import org.jetbrains.idea.svn.update.RelocateClient;
import org.jetbrains.idea.svn.update.UpdateClient;
import org.jetbrains.idea.svn.upgrade.UpgradeClient;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;

import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class ClientFactory {

  @Nonnull
  protected SvnVcs myVcs;

  protected AddClient addClient;
  protected AnnotateClient annotateClient;
  protected ContentClient contentClient;
  protected HistoryClient historyClient;
  protected RevertClient revertClient;
  protected DeleteClient deleteClient;
  protected StatusClient statusClient;
  protected InfoClient infoClient;
  protected CopyMoveClient copyMoveClient;
  protected ConflictClient conflictClient;
  protected PropertyClient propertyClient;
  protected MergeClient mergeClient;
  protected ChangeListClient changeListClient;
  protected CheckoutClient checkoutClient;
  protected LockClient myLockClient;
  protected CleanupClient myCleanupClient;
  protected RelocateClient myRelocateClient;
  protected VersionClient myVersionClient;
  protected ImportClient myImportClient;
  protected ExportClient myExportClient;
  protected UpgradeClient myUpgradeClient;
  protected BrowseClient myBrowseClient;
  protected DiffClient myDiffClient;
  protected CheckinClient myCheckinClient;
  protected RepositoryFeaturesClient myRepositoryFeaturesClient;

  @Nonnull
  private final Map<Class, Class> myClientImplementations = ContainerUtil.newHashMap();

  protected ClientFactory(@Nonnull SvnVcs vcs) {
    myVcs = vcs;
    setup();
  }

  protected abstract void setup();

  protected <T extends SvnClient> void put(@Nonnull Class<T> type, @Nonnull Class<? extends T> implementation) {
    myClientImplementations.put(type, implementation);
  }

  @SuppressWarnings("unchecked")
  @Nonnull
  protected <T extends SvnClient> Class<? extends T> get(@Nonnull Class<T> type) {
    Class<? extends T> implementation = myClientImplementations.get(type);

    if (implementation == null) {
      throw new IllegalArgumentException("No implementation registered for " + type);
    }

    return implementation;
  }

  /**
   * TODO: Provide more robust way for the default settings here - probably some default Command instance could be used.
   */
  @Nonnull
  public <T extends SvnClient> T create(@Nonnull Class<T> type, boolean isActive) {
    T client = prepare(ReflectionUtil.newInstance(get(type)));
    client.setIsActive(isActive);

    return client;
  }

  @Nonnull
  public AddClient createAddClient() {
    return prepare(addClient);
  }

  @Nonnull
  public AnnotateClient createAnnotateClient() {
    return prepare(annotateClient);
  }

  @Nonnull
  public ContentClient createContentClient() {
    return prepare(contentClient);
  }

  @Nonnull
  public HistoryClient createHistoryClient() {
    return prepare(historyClient);
  }

  @Nonnull
  public RevertClient createRevertClient() {
    return prepare(revertClient);
  }

  @Nonnull
  public StatusClient createStatusClient() {
    return prepare(statusClient);
  }

  @Nonnull
  public StatusClient createStatusClient(@Nullable ISVNStatusFileProvider provider, @Nonnull ProgressTracker handler) {
    return createStatusClient();
  }

  @Nonnull
  public InfoClient createInfoClient() {
    return prepare(infoClient);
  }

  // TODO: Update this in same like other clients - move to corresponding package, rename clients
  // New instances should be always created by this method, as setXxx() methods are currently used in update logic
  @Nonnull
  public abstract UpdateClient createUpdateClient();

  @Nonnull
  public DeleteClient createDeleteClient() {
    return prepare(deleteClient);
  }

  @Nonnull
  public CopyMoveClient createCopyMoveClient() {
    return prepare(copyMoveClient);
  }

  @Nonnull
  public ConflictClient createConflictClient() {
    return prepare(conflictClient);
  }

  @Nonnull
  public PropertyClient createPropertyClient() {
    return prepare(propertyClient);
  }

  @Nonnull
  public MergeClient createMergeClient() {
    return prepare(mergeClient);
  }

  @Nonnull
  public ChangeListClient createChangeListClient() {
    return prepare(changeListClient);
  }

  @Nonnull
  public CheckoutClient createCheckoutClient() {
    return prepare(checkoutClient);
  }

  @Nonnull
  public LockClient createLockClient() {
    return prepare(myLockClient);
  }

  @Nonnull
  public CleanupClient createCleanupClient() {
    return prepare(myCleanupClient);
  }

  @Nonnull
  public RelocateClient createRelocateClient() {
    return prepare(myRelocateClient);
  }

  @Nonnull
  public VersionClient createVersionClient() {
    return prepare(myVersionClient);
  }

  @Nonnull
  public ImportClient createImportClient() {
    return prepare(myImportClient);
  }

  @Nonnull
  public ExportClient createExportClient() {
    return prepare(myExportClient);
  }

  @Nonnull
  public UpgradeClient createUpgradeClient() {
    return prepare(myUpgradeClient);
  }

  @Nonnull
  public BrowseClient createBrowseClient() {
    return prepare(myBrowseClient);
  }

  @Nonnull
  public DiffClient createDiffClient() {
    return prepare(myDiffClient);
  }

  @Nonnull
  public CheckinClient createCheckinClient() {
    return prepare(myCheckinClient);
  }

  @Nonnull
  public RepositoryFeaturesClient createRepositoryFeaturesClient() {
    return prepare(myRepositoryFeaturesClient);
  }

  @Nonnull
  protected <T extends SvnClient> T prepare(@Nonnull T client) {
    client.setVcs(myVcs);
    client.setFactory(this);
    client.setIsActive(true);

    return client;
  }
}
