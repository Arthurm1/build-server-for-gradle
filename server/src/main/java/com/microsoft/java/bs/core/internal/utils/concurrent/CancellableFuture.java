// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.

package com.microsoft.java.bs.core.internal.utils.concurrent;

import java.util.concurrent.CompletableFuture;

/**
 * {@link CompletableFuture} implementation that triggers a {@link Runnable} when cancelled.
 * This does not cancel any other {@link CompletableFuture} in the chain.
 * Reference:
 * https://github.com/JetBrains/intellij-scala/blob/9395de0f3ae6e4c3f7411edabc5374e6595162f5/bsp/src/org/jetbrains/bsp/protocol/session/CancellableFuture.java
 */
public class CancellableFuture<T> extends CompletableFuture<T> {
  
  private final Runnable cancelRunnable;

  private CancellableFuture(Runnable cancelRunnable) {
    this.cancelRunnable = cancelRunnable;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    if (cancelRunnable != null) {
      cancelRunnable.run();
    }
    return super.cancel(mayInterruptIfRunning);
  }

  @Override
  public <U> CancellableFuture<U> newIncompleteFuture() {
    return new CancellableFuture<>(cancelRunnable);
  }

  /**
   * Add CancellableFuture to endOfChain so it can pass on the endOfChain result.
   *
   * @param <U> endOfChain result type
   * @param endOfChain end of a chain of CompletableFutures
   * @param cancelRunnable code to run when cancellation is triggered
   * @return CancellableFuture
   */
  public static <U> CancellableFuture<U> from(CompletableFuture<U> endOfChain,
      Runnable cancelRunnable) {
    CancellableFuture<U> result = new CancellableFuture<>(cancelRunnable);
    endOfChain.whenComplete((value, error) -> {
      if (error != null) {
        result.completeExceptionally(error);
      } else {
        result.complete(value);
      }
    });
    return result;
  }
}