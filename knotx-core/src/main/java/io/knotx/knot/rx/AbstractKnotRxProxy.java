/*
 * Copyright (C) 2016 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.knotx.knot.rx;

import io.knotx.dataobjects.Fragment;
import io.knotx.dataobjects.KnotContext;
import io.knotx.proxy.KnotProxy;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import rx.Single;

/**
 * Abstract class that should be root for all custom knots
 */
public abstract class AbstractKnotRxProxy implements KnotProxy {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractKnotRxProxy.class);

  protected static final String DEFAULT_TRANSITION = "next";

  @Override
  public void process(KnotContext knotContext, Handler<AsyncResult<KnotContext>> result) {
    if (shouldProcess(knotContext)) {
      processRequest(knotContext)
          .subscribe(
              ctx -> result.handle(Future.succeededFuture(ctx)),
              error -> {
                LOGGER.error("Error happened during Knot Context processing", error);
                result.handle(Future.succeededFuture(processError(knotContext, error)));
              }
          );
    } else {
      knotContext.setTransition(StringUtils.isBlank(knotContext.getTransition()) ?
          DEFAULT_TRANSITION : knotContext.getTransition());
      result.handle(Future.succeededFuture(knotContext));
    }
  }

  /**
   * Consumes a {@link KnotContext} messages from the Server and returns modified, processed
   * context. Basically this method is responsible for the whole business logic that your Knot will
   * be performing.
   *
   * @param knotContext message from the Server with processing context.
   * @return a {@link Single} that emits a processed and modified {@link KnotContext}.
   */
  protected abstract Single<KnotContext> processRequest(KnotContext knotContext);

  /**
   * Method lets you decide whether the Fragment should be processed by your Knot or not.
   *
   * @param knots set of all Knots names that occurred in the current {@link KnotContext}.
   * @return <tt>true</tt> if this Knot should process current {@link KnotContext}.
   */
  protected abstract boolean shouldProcess(Set<String> knots);

  /**
   * Handles any Exception thrown during processing, and is responsible for preparing the proper
   * {@link KnotContext} on such occasions, these will simply finish processing flows, as any error
   * generated by a Knot will be immediately returned to the page visitor.
   *
   * @param knotContext current context.
   * @param error the error that just occurred.
   * @return context prepared with proper content that notifies about the error and incorrect
   * processing.
   */
  protected abstract KnotContext processError(KnotContext knotContext, Throwable error);

  private boolean shouldProcess(KnotContext context) {
    Set<String> knots = Optional.ofNullable(context)
        .map(KnotContext::getFragments)
        .map(this::getKnotSet)
        .orElse(Collections.emptySet());
    return shouldProcess(knots);
  }

  private Set<String> getKnotSet(List<Fragment> fragments) {
    return
        fragments.stream()
            .map(Fragment::knots)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
  }
}
