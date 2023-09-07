/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.status;

import consulo.util.lang.ObjectUtil;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
public enum StatusType {

  // currently used to represent some not used status types from SVNKit
  UNUSED("unused"),

  INAPPLICABLE("inapplicable"),
  UNKNOWN("unknown"),
  UNCHANGED("unchanged"),
  MISSING("missing"),
  OBSTRUCTED("obstructed"),
  CHANGED("changed"),
  MERGED("merged"),
  CONFLICTED("conflicted"),

  STATUS_NONE("none"),
  STATUS_NORMAL("normal", ' '),
  STATUS_MODIFIED("modified", 'M'),
  STATUS_ADDED("added", 'A'),
  STATUS_DELETED("deleted", 'D'),
  STATUS_UNVERSIONED("unversioned", '?'),
  STATUS_MISSING("missing", '!'),
  STATUS_REPLACED("replaced", 'R'),
  STATUS_CONFLICTED("conflicted", 'C'),
  STATUS_OBSTRUCTED("obstructed", '~'),
  STATUS_IGNORED("ignored", 'I'),
  // directory is incomplete - checkout or update was interrupted
  STATUS_INCOMPLETE("incomplete", '!'),
  STATUS_EXTERNAL("external", 'X');

  private static final String STATUS_PREFIX = "STATUS_";

  @Nonnull
  private static final Map<String, StatusType> ourOtherStatusTypes = new HashMap<>();
  @Nonnull
  private static final Map<String, StatusType> ourStatusTypesForStatusOperation = new HashMap<>();

  static {
    for (StatusType action : StatusType.values()) {
      register(action);
    }
  }

  private String myName;
  private char myCode;

  StatusType(String name) {
    this(name, ' ');
  }

  StatusType(String name, char code) {
    myName = name;
    myCode = code;
  }

  public char getCode() {
    return myCode;
  }

  public String toString() {
    return myName;
  }

  private static void register(@Nonnull StatusType action) {
    (action.name().startsWith(STATUS_PREFIX) ? ourStatusTypesForStatusOperation : ourOtherStatusTypes).put(action.myName, action);
  }

  @Nonnull
  public static StatusType from(@Nonnull SVNStatusType type) {
    StatusType result = ourOtherStatusTypes.get(type.toString());

    // CONFLICTED, OBSTRUCTED, MISSING status types have corresponding STATUS_* analogs with same names - so additional check added when
    // converting from SVNKit values
    if (type != SVNStatusType.CONFLICTED && type != SVNStatusType.OBSTRUCTED && type != SVNStatusType.MISSING) {
      result = ObjectUtil.chooseNotNull(ourStatusTypesForStatusOperation.get(type.toString()), result);
    }

    return ObjectUtil.notNull(result, UNUSED);
  }

  @Nullable
  public static StatusType forStatusOperation(@Nonnull String statusName) {
    return ourStatusTypesForStatusOperation.get(statusName);
  }
}
