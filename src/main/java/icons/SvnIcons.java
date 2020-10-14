/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package icons;

import consulo.annotation.DeprecationInfo;
import consulo.apache.subversion.icon.SubversionIconGroup;
import consulo.ui.image.Image;

@Deprecated
@DeprecationInfo("Use SubversionIconGroup")
public class SvnIcons {
  public static final Image AllRevisions = SubversionIconGroup.allRevisions();
  public static final Image Common = SubversionIconGroup.Common();
  public static final Image ConfigureBranches = SubversionIconGroup.ConfigureBranches();
  public static final Image Conflictc = SubversionIconGroup.conflictc();
  public static final Image Conflictcp = SubversionIconGroup.conflictcp();
  public static final Image Conflictct = SubversionIconGroup.conflictct();
  public static final Image Conflictctp = SubversionIconGroup.conflictctp();
  public static final Image Conflictp = SubversionIconGroup.conflictp();
  public static final Image Conflictt = SubversionIconGroup.conflictt();
  public static final Image Conflicttp = SubversionIconGroup.conflicttp();
  public static final Image FilterIntegrated = SubversionIconGroup.FilterIntegrated();
  public static final Image FilterNotIntegrated = SubversionIconGroup.FilterNotIntegrated();
  public static final Image FilterOthers = SubversionIconGroup.FilterOthers();
  public static final Image Integrated = SubversionIconGroup.Integrated();
  public static final Image IntegrateToBranch = SubversionIconGroup.IntegrateToBranch();
  public static final Image IntegrationStatusUnknown = SubversionIconGroup.IntegrationStatusUnknown();
  public static final Image MarkAsMerged = SubversionIconGroup.MarkAsMerged();
  public static final Image MarkAsNotMerged = SubversionIconGroup.MarkAsNotMerged();
  public static final Image MergeSourcesDetails = SubversionIconGroup.mergeSourcesDetails();
  public static final Image Notintegrated = SubversionIconGroup.Notintegrated();
  public static final Image OnDefault = SubversionIconGroup.OnDefault();
  public static final Image PropertiesDiff = SubversionIconGroup.PropertiesDiff();
  public static final Image PropertiesDiffWithLocal = SubversionIconGroup.PropertiesDiffWithLocal();
  public static final Image ShowIntegratedFrom = SubversionIconGroup.ShowIntegratedFrom();
  public static final Image ShowIntegratedTo = SubversionIconGroup.ShowIntegratedTo();
  public static final Image ShowWorkingCopies = SubversionIconGroup.ShowWorkingCopies();
  public static final Image UndoIntegrateToBranch = SubversionIconGroup.UndoIntegrateToBranch();
}
