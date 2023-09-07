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
package org.jetbrains.idea.svn.commandLine;

import consulo.ide.impl.idea.util.io.BinaryOutputReader;
import consulo.process.internal.OSProcessHandler;
import consulo.process.io.BaseDataReader;
import consulo.process.io.ProcessIOExecutorService;
import consulo.util.io.CharsetToolkit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Future;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnProcessHandler extends OSProcessHandler {

  private final boolean myForceUtf8;
  private final boolean myForceBinary;
  @Nonnull
  private final ByteArrayOutputStream myBinaryOutput;

  public SvnProcessHandler(@Nonnull Process process, @Nonnull String commandLine, boolean forceUtf8, boolean forceBinary) {
    super(process, commandLine);

    myForceUtf8 = forceUtf8;
    myForceBinary = forceBinary;
    myBinaryOutput = new ByteArrayOutputStream();
  }

  @Nonnull
  public ByteArrayOutputStream getBinaryOutput() {
    return myBinaryOutput;
  }

  @Nullable
  @Override
  public Charset getCharset() {
    return myForceUtf8 ? CharsetToolkit.UTF8_CHARSET : super.getCharset();
  }

  @Nonnull
  @Override
  protected BaseDataReader createOutputDataReader() {
    if (myForceBinary) {
      return new SimpleBinaryOutputReader(myProcess.getInputStream(), readerOptions().policy());
    }
    else {
      return super.createOutputDataReader();
    }
  }

  private class SimpleBinaryOutputReader extends BinaryOutputReader {
    private SimpleBinaryOutputReader(@Nonnull InputStream stream, @Nonnull SleepingPolicy sleepingPolicy) {
      super(stream, sleepingPolicy);
      start(myPresentableName);
    }

    @Override
    protected void onBinaryAvailable(@Nonnull byte[] data, int size) {
      myBinaryOutput.write(data, 0, size);
    }

    @Nonnull
    @Override
    protected Future<?> executeOnPooledThread(@Nonnull Runnable runnable) {
      return ProcessIOExecutorService.INSTANCE.submit(runnable);
    }
  }
}
