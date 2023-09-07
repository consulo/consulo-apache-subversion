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
package org.jetbrains.idea.svn;

import consulo.annotation.DeprecationInfo;
import consulo.apache.subversion.icon.SubversionIconGroup;
import consulo.ui.image.Image;

@Deprecated(forRemoval = true)
@DeprecationInfo("Use SubversionIconGroup")
public class SvnIcons {
  public static final Image AllRevisions = SubversionIconGroup.allrevisions();
  public static final Image Common = SubversionIconGroup.common();
  public static final Image ConfigureBranches = SubversionIconGroup.configurebranches();
  public static final Image Conflictc = SubversionIconGroup.conflictc();
  public static final Image Conflictcp = SubversionIconGroup.conflictcp();
  public static final Image Conflictct = SubversionIconGroup.conflictct();
  public static final Image Conflictctp = SubversionIconGroup.conflictctp();
  public static final Image Conflictp = SubversionIconGroup.conflictp();
  public static final Image Conflictt = SubversionIconGroup.conflictt();
  public static final Image Conflicttp = SubversionIconGroup.conflicttp();
  public static final Image FilterIntegrated = SubversionIconGroup.filterintegrated();
  public static final Image FilterNotIntegrated = SubversionIconGroup.filternotintegrated();
  public static final Image FilterOthers = SubversionIconGroup.filterothers();
  public static final Image Integrated = SubversionIconGroup.integrated();
  public static final Image IntegrateToBranch = SubversionIconGroup.integratetobranch();
  public static final Image IntegrationStatusUnknown = SubversionIconGroup.integrationstatusunknown();
  public static final Image MarkAsMerged = SubversionIconGroup.markasmerged();
  public static final Image MarkAsNotMerged = SubversionIconGroup.markasnotmerged();
  public static final Image MergeSourcesDetails = SubversionIconGroup.mergesourcesdetails();
  public static final Image Notintegrated = SubversionIconGroup.notintegrated();
  public static final Image OnDefault = SubversionIconGroup.ondefault();
  public static final Image PropertiesDiff = SubversionIconGroup.propertiesdiff();
  public static final Image PropertiesDiffWithLocal = SubversionIconGroup.propertiesdiffwithlocal();
  public static final Image ShowIntegratedFrom = SubversionIconGroup.showintegratedfrom();
  public static final Image ShowIntegratedTo = SubversionIconGroup.showintegratedto();
  public static final Image ShowWorkingCopies = SubversionIconGroup.showworkingcopies();
  public static final Image UndoIntegrateToBranch = SubversionIconGroup.undointegratetobranch();
}
