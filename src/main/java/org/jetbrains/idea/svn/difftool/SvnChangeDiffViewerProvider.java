package org.jetbrains.idea.svn.difftool;

import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.impl.DiffViewerWrapper;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffViewerWrapperProvider;
import com.intellij.util.ThreeState;
import consulo.util.dataholder.UserDataHolder;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnChangeProvider;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffRequest;
import org.jetbrains.idea.svn.history.PropertyRevision;
import org.jetbrains.idea.svn.properties.PropertyData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class SvnChangeDiffViewerProvider implements ChangeDiffViewerWrapperProvider {
  private static final Logger LOG = Logger.getInstance(SvnChangeDiffViewerProvider.class);

  @Nonnull
  @Override
  public ThreeState isEquals(@Nonnull Change change1, @Nonnull Change change2) {
    Change layer1 = getSvnChangeLayer(change1);
    Change layer2 = getSvnChangeLayer(change2);
    if (layer1 != null || layer2 != null) {
      if (layer1 == null || layer2 == null) return ThreeState.NO;
    }
    return ThreeState.UNSURE;
  }

  @Override
  public boolean canCreate(@Nullable Project project, @Nonnull Change change) {
    return getSvnChangeLayer(change) != null; // TODO: do not show, if no properties are set in both revisions ?
  }

  @Nonnull
  @Override
  public DiffViewerWrapper process(@Nonnull ChangeDiffRequestProducer presentable,
                                   @Nonnull UserDataHolder context,
                                   @Nonnull ProgressIndicator indicator) throws DiffRequestProducerException, ProcessCanceledException {
    // TODO: support properties conflict for three-way-diff

    DiffRequest propertyRequest = createPropertyRequest(presentable.getChange(), indicator);
    return new SvnDiffViewerWrapper(propertyRequest);
  }

  @Nonnull
  private static SvnPropertiesDiffRequest createPropertyRequest(@Nonnull Change change, @Nonnull ProgressIndicator indicator)
    throws DiffRequestProducerException {
    try {
      Change propertiesChange = getSvnChangeLayer(change);
      if (propertiesChange == null) throw new DiffRequestProducerException(SvnBundle.getString("diff.cant.get.properties.changes"));

      ContentRevision bRevRaw = propertiesChange.getBeforeRevision();
      ContentRevision aRevRaw = propertiesChange.getAfterRevision();

      if (bRevRaw != null && !(bRevRaw instanceof PropertyRevision)) {
        LOG.warn("Before change is not PropertyRevision");
        throw new DiffRequestProducerException(SvnBundle.getString("diff.cant.get.properties.changes"));
      }
      if (aRevRaw != null && !(aRevRaw instanceof PropertyRevision)) {
        LOG.warn("After change is not PropertyRevision");
        throw new DiffRequestProducerException(SvnBundle.getString("diff.cant.get.properties.changes"));
      }

      PropertyRevision bRev = (PropertyRevision)bRevRaw;
      PropertyRevision aRev = (PropertyRevision)aRevRaw;

      indicator.checkCanceled();
      List<PropertyData> bContent = bRev != null ? bRev.getProperties() : null;

      indicator.checkCanceled();
      List<PropertyData> aContent = aRev != null ? aRev.getProperties() : null;

      if (aRev == null && bRev == null) throw new DiffRequestProducerException(SvnBundle.getString("diff.cant.get.properties.changes"));

      ContentRevision bRevMain = change.getBeforeRevision();
      ContentRevision aRevMain = change.getAfterRevision();
      String title1 = bRevMain != null ? StringUtil.nullize(bRevMain.getRevisionNumber().asString()) : null;
      String title2 = aRevMain != null ? StringUtil.nullize(aRevMain.getRevisionNumber().asString()) : null;

      return new SvnPropertiesDiffRequest(bContent, aContent, title1, title2);
    }
    catch (VcsException e) {
      throw new DiffRequestProducerException(e);
    }
  }

  @Nullable
  private static Change getSvnChangeLayer(@Nonnull Change change) {
    for (Map.Entry<String, Change> entry : change.getOtherLayers().entrySet()) {
      if (SvnChangeProvider.PROPERTY_LAYER.equals(entry.getKey())) {
        if (change.getOtherLayers().size() != 1) LOG.warn("Some of change layers ignored");
        return entry.getValue();
      }
    }
    return null;
  }
}
