// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.transport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutionException;

/**
 * A named pipe stream implementation.
 */
public class NamedPipeStream {
  private final String pipeName;
  private StreamProvider provider;

  /**
   * Constructor.
   *
   * @param pipeName name of pipe.
   */
  public NamedPipeStream(String pipeName) {
    this.pipeName = pipeName;
  }

  private interface StreamProvider {
    InputStream getInputStream();

    OutputStream getOutputStream();
  }

  /**
   * getSelectedStream.
   */
  private StreamProvider getSelectedStream() {
    if (provider == null) {
      provider = createProvider();
    }
    return provider;
  }

  private StreamProvider createProvider() {
    PipeStreamProvider pipeStreamProvider = new PipeStreamProvider();
    pipeStreamProvider.initializeNamedPipe(pipeName);
    return pipeStreamProvider;
  }

  /**
   * get the input stream.
   *
   * @return input stream
   */
  public InputStream getInputStream() {
    return getSelectedStream().getInputStream();
  }

  /**
   * get the output stream.
   *
   * @return output stream
   */
  public OutputStream getOutputStream() {
    return getSelectedStream().getOutputStream();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  /**
   * PipeStreamProvider.
   */
  private static class PipeStreamProvider implements StreamProvider {

    private InputStream input;
    private OutputStream output;

    @Override
    public InputStream getInputStream() {
      return input;
    }

    @Override
    public OutputStream getOutputStream() {
      return output;
    }

    private void initializeNamedPipe(final String pipeName) {
      File pipeFile = new File(pipeName);
      try {
        if (isWindows()) {
          attemptWindowsConnection(pipeFile);
        } else {
          attemptUnixConnection(pipeFile);
        }
      } catch (IOException e) {
        throw new IllegalStateException("Error initializing the named pipe", e);
      }
    }

    private void attemptWindowsConnection(File pipeFile) throws IOException {
      AsynchronousFileChannel channel = AsynchronousFileChannel.open(pipeFile.toPath(),
              StandardOpenOption.READ, StandardOpenOption.WRITE);

      PipeReader reader = buffer -> {
        try {
          return channel.read(buffer, 0).get();
        } catch (InterruptedException | ExecutionException e) {
          throw new IOException("Error in reading from Windows pipe", e);
        }
      };
      PipeWriter writer = buffer -> {
        try {
          return channel.write(buffer, 0).get();
        } catch (InterruptedException | ExecutionException e) {
          throw new IOException("Error in writing to Windows pipe", e);
        }
      };
      input = new NamedPipeInputStream(reader);
      output = new NamedPipeOutputStream(writer);
    }

    private void attemptUnixConnection(File pipeFile) throws IOException {
      UnixDomainSocketAddress socketAddress = UnixDomainSocketAddress.of(pipeFile.toPath());
      SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
      channel.connect(socketAddress);
      input = new NamedPipeInputStream(channel::read);
      output = new NamedPipeOutputStream(channel::write);
    }
  }

  @FunctionalInterface
  private interface PipeReader {
    int read(ByteBuffer byteBuffer) throws IOException;
  }

  /**
   * NamedPipeInputStream.
   */
  private static class NamedPipeInputStream extends InputStream {

    private final PipeReader reader;
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private int readyBytes = 0;

    private NamedPipeInputStream(PipeReader reader) {
      this.reader = reader;
    }

    @Override
    public int read() throws IOException {
      if (buffer.position() < readyBytes) {
        return buffer.get() & 0xFF;
      }
      buffer.clear();
      readyBytes = reader.read(buffer);
      if (readyBytes == -1) {
        return -1; // EOF
      }
      buffer.flip();
      return buffer.get() & 0xFF;
    }
  }

  @FunctionalInterface
  private interface PipeWriter {
    int write(ByteBuffer byteBuffer) throws IOException;
  }

  /**
   * NamedPipeOutputStream.
   */
  private static class NamedPipeOutputStream extends OutputStream {

    private final PipeWriter writer;
    private final ByteBuffer buffer = ByteBuffer.allocate(1);

    private NamedPipeOutputStream(PipeWriter writer) {
      this.writer = writer;
    }

    @Override
    public void write(int b) throws IOException {
      buffer.clear();
      buffer.put((byte) b);
      buffer.position(0);
      writer.write(buffer);
    }

    @Override
    public void write(byte[] b) throws IOException {
      final int buffer_size = 1024;
      int blocks = b.length / buffer_size;
      int writeBytes = 0;
      for (int i = 0; i <= blocks; i++) {
        int offset = i * buffer_size;
        int length = Math.min(b.length - writeBytes, buffer_size);
        if (length <= 0) {
          break;
        }
        writeBytes += length;
        ByteBuffer localBuffer = ByteBuffer.wrap(b, offset, length);
        writer.write(localBuffer);
      }
    }
  }
}
