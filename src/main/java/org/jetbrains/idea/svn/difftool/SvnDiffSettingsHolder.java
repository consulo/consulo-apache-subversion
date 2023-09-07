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
package org.jetbrains.idea.svn.difftool;

import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.diff.DiffPlaces;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.diff.util.DiffUtil;
import consulo.util.dataholder.Key;
import consulo.util.xml.serializer.annotation.MapAnnotation;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@Singleton
@State(name = "SvnDiffSettings", storages = @Storage(file = DiffUtil.DIFF_CONFIG))
public class SvnDiffSettingsHolder implements PersistentStateComponent<SvnDiffSettingsHolder.State> {
  public static final Key<SvnDiffSettings> KEY = Key.create("SvnDiffSettings");

  private static class SharedSettings {
  }

  private static class PlaceSettings {
    public float SPLITTER_PROPORTION = 0.9f;
    public boolean HIDE_PROPERTIES = false;
  }

  public static class SvnDiffSettings {
    @Nonnull
    public SharedSettings SHARED_SETTINGS = new SharedSettings();
    @Nonnull
    public PlaceSettings PLACE_SETTINGS = new PlaceSettings();

    public SvnDiffSettings() {
    }

    public SvnDiffSettings(@Nonnull SharedSettings SHARED_SETTINGS,
                           @Nonnull PlaceSettings PLACE_SETTINGS) {
      this.SHARED_SETTINGS = SHARED_SETTINGS;
      this.PLACE_SETTINGS = PLACE_SETTINGS;
    }

    public boolean isHideProperties() {
      return PLACE_SETTINGS.HIDE_PROPERTIES;
    }

    public void setHideProperties(boolean value) {
      PLACE_SETTINGS.HIDE_PROPERTIES = value;
    }

    public float getSplitterProportion() {
      return PLACE_SETTINGS.SPLITTER_PROPORTION;
    }

    public void setSplitterProportion(float value) {
      PLACE_SETTINGS.SPLITTER_PROPORTION = value;
    }

    //
    // Impl
    //

    @Nonnull
    public static SvnDiffSettings getSettings() {
      return getSettings(null);
    }

    @Nonnull
    public static SvnDiffSettings getSettings(@Nullable String place) {
      return getInstance().getSettings(place);
    }
  }

  @Nonnull
  public SvnDiffSettings getSettings(@Nullable String place) {
    if (place == null) place = DiffPlaces.DEFAULT;

    PlaceSettings placeSettings = myState.PLACES_MAP.get(place);
    if (placeSettings == null) {
      placeSettings = new PlaceSettings();
      myState.PLACES_MAP.put(place, placeSettings);
    }
    return new SvnDiffSettings(myState.SHARED_SETTINGS, placeSettings);
  }

  public static class State {
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, PlaceSettings> PLACES_MAP = new HashMap<>();
    public SharedSettings SHARED_SETTINGS = new SharedSettings();
  }

  private State myState = new State();

  @Nonnull
  public State getState() {
    return myState;
  }

  public void loadState(State state) {
    myState = state;
  }

  public static SvnDiffSettingsHolder getInstance() {
    return ServiceManager.getService(SvnDiffSettingsHolder.class);
  }
}
