/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs;

import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import org.jetbrains.idea.svn.properties.PropertyValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: Jun 21, 2006
 * Time: 6:37:41 PM
 */
public class SetKeywordsDialog extends DialogWrapper {

  private static final List<String> KNOWN_KEYWORDS =
    ContainerUtil.newArrayList("Id", "HeadURL", "LastChangedDate", "LastChangedRevision", "LastChangedBy");

  private static final Map<String, String> KNOWN_KEYWORD_ALIASES = Map.of(
    "URL", "HeadURL",
    "Date", "LastChangedDate",
    "Rev", "LastChangedRevision",
    "Author", "LastChangedBy"
  );

  @Nullable
  private final PropertyValue myKeywordsValue;
  @Nonnull
  private final List<JCheckBox> myKeywordOptions;

  protected SetKeywordsDialog(Project project, @Nullable PropertyValue keywordsValue) {
    super(project, false);
    myKeywordOptions = ContainerUtil.newArrayList();
    myKeywordsValue = keywordsValue;

    setTitle("SVN Keywords");
    setResizable(false);
    init();
  }

  @Nullable
  public String getKeywords() {
    List<JCheckBox> selectedKeywords = ContainerUtil.filter(myKeywordOptions, new Condition<JCheckBox>() {
      @Override
      public boolean value(@Nonnull JCheckBox keywordOption) {
        return keywordOption.isSelected();
      }
    });

    return StringUtil.nullize(StringUtil.join(selectedKeywords, keywordOption -> keywordOption.getText(), " "));
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel("Select keywords to set: "), BorderLayout.NORTH);
    JPanel buttonsPanel = new JPanel(new GridLayout(5, 1));

    for (String keyword : KNOWN_KEYWORDS) {
      JCheckBox keywordOption = new JCheckBox(keyword);

      myKeywordOptions.add(keywordOption);
      buttonsPanel.add(keywordOption);
    }

    panel.add(buttonsPanel, BorderLayout.CENTER);

    return panel;
  }

  @Override
  protected void init() {
    super.init();

    updateKeywordOptions();
  }

  private void updateKeywordOptions() {
    Set<String> keywords = parseKeywords(myKeywordsValue);

    for (JCheckBox keywordOption : myKeywordOptions) {
      keywordOption.setSelected(keywords.contains(keywordOption.getText()));
    }
  }

  /**
   * TODO: Subversion 1.8 also allow defining custom keywords (in "svn:keywords" property value). But currently it is unnecessary for this
   * TODO: dialog.
   */
  @Nonnull
  private static Set<String> parseKeywords(@Nullable PropertyValue keywordsValue) {
    Set<String> result = new HashSet<>();

    if (keywordsValue != null) {
      for (String keyword : StringUtil.split(PropertyValue.toString(keywordsValue), " ")) {
        result.add(ObjectUtil.notNull(KNOWN_KEYWORD_ALIASES.get(keyword), keyword));
      }
    }

    return result;
  }
}
