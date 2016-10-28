/*
 * Knot.x - Reactive microservice assembler - Action Knot Verticle
 *
 * Copyright (C) 2016 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cognifide.knotx.knot.action;

import com.cognifide.knotx.adapter.common.http.AllowedHeadersFilter;
import com.cognifide.knotx.adapter.common.http.MultiMapCollector;
import com.cognifide.knotx.dataobjects.ClientRequest;
import com.cognifide.knotx.dataobjects.ClientResponse;
import com.cognifide.knotx.dataobjects.KnotContext;
import com.cognifide.knotx.fragments.Fragment;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.core.eventbus.Message;
import rx.Observable;

public class ActionKnotVerticle extends AbstractVerticle {

  public static final String DEFAULT_TRANSITION = "next";
  private static final String ACTION_FRAGMENT_IDENTIFIER_REGEXP = "form-([A-Za-z0-9]+)*";
  private static final Pattern ACTION_FRAGMENT_IDENTIFIER_PATTERN = Pattern.compile(ACTION_FRAGMENT_IDENTIFIER_REGEXP);
  private static final String ACTION_FORM_ATTRIBUTES_PATTERN = "data-knotx-.*";
  private static final String ACTION_FORM_ACTION_ATTRIBUTE = "data-knotx-action";

  private static final Logger LOGGER = LoggerFactory.getLogger(ActionKnotVerticle.class);

  private ActionKnotConfiguration configuration;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    configuration = new ActionKnotConfiguration(config());
  }

  @Override
  public void start() throws Exception {
    LOGGER.debug("Starting <{}>", this.getClass().getName());

    vertx.eventBus().<JsonObject>consumer(configuration.address())
        .handler(message -> Observable.just(message)
            .doOnNext(this::traceMessage)
            .subscribe(
                result -> {
                  handle(result, message::reply);
                },
                error -> {
                  LOGGER.error("Error occured in Action Knot.", error);
                  message.reply(processError(new KnotContext(message.body()), error).toJson());
                }
            ));
  }

  private void handle(Message<JsonObject> jsonObject, Handler<JsonObject> handler) {
    KnotContext knotContext = new KnotContext(jsonObject.body());
    if (HttpMethod.POST.equals(knotContext.clientRequest().method())) {
      handleFormAction(knotContext, handler);
    } else {
      handleGetMethod(handler, knotContext);
    }

  }

  private void handleFormAction(KnotContext knotContext, Handler<JsonObject> handler) {
    LOGGER.trace("Process form for {} ", knotContext);
    Fragment currentFragment = knotContext.fragments()
        .flatMap(fragments -> fragments.stream()
            .filter(fragment -> isCurrentFormFragment(fragment, knotContext))
            .findFirst())
        .orElseThrow(() -> {
          String formIdentifier = getFormIdentifier(knotContext).orElse("EMPTY");
          LOGGER.error("Could not find fragment with id [{}] in fragments [{}]", formIdentifier, knotContext.fragments());
          return new NoSuchElementException("Fragment for [" + formIdentifier + "] not found");
        });


    String actionAdapterName = Optional.ofNullable(getScriptContentDocument(currentFragment)
        .getElementsByAttribute(ACTION_FORM_ACTION_ATTRIBUTE).first())
        .map(element -> element.attr(ACTION_FORM_ACTION_ATTRIBUTE))
        .orElseThrow(() -> {
          LOGGER.error("Could not find action adapter name in current fragment [{}].", currentFragment);
          return new NoSuchElementException("Could not find action adapter name");
        });

    ActionKnotConfiguration.AdapterMetadata adapterMetadata = configuration.adapterMetadatas().stream()
        .filter(item -> item.getName().equals(actionAdapterName))
        .findFirst()
        .orElseThrow(() -> {
          LOGGER.error("Could not find adapter name [{}] in configuration [{}]", actionAdapterName, configuration.adapterMetadatas());
          return new NoSuchElementException("Action adapter not found!");
        });

    vertx.eventBus().<JsonObject>sendObservable(adapterMetadata.getAddress(), prepareRequest(knotContext, adapterMetadata)).subscribe(
        msg -> {
          ClientResponse clientResponse = new ClientResponse(msg.body().getJsonObject("clientResponse"));
          String signal = msg.body().getString("signal");

          String redirectLocation = Optional.ofNullable(getScriptContentDocument(currentFragment)
              .getElementsByAttribute("data-knotx-on-" + signal).first())
              .map(element -> element.attr("data-knotx-on-" + signal))
              .orElseThrow(() -> {
                LOGGER.error("Could not find action adapter name in current fragment [{}].", currentFragment);
                return new NoSuchElementException("Could not action adapter name");
              });

          if (shouldRedirect(redirectLocation)) {
            LOGGER.trace("Request redirected to [{}]", redirectLocation);
            knotContext.clientResponse().setStatusCode(HttpResponseStatus.MOVED_PERMANENTLY);
            MultiMap headers = knotContext.clientResponse().headers();
            headers.addAll(getFilteredHeaders(clientResponse.headers(), adapterMetadata.getAllowedResponseHeaders()));
            headers.set(HttpHeaders.LOCATION.toString(), redirectLocation);

            knotContext.clientResponse().setHeaders(headers);
            knotContext.clearFragments();
          } else {
            LOGGER.trace("Request next transition to [{}]", DEFAULT_TRANSITION);
            JsonObject actionContext = new JsonObject()
                .put("_result", new JsonObject(clientResponse.body().toString()))
                .put("_response", clientResponse.clearBody().toJson());

            currentFragment.getContext().put("action", actionContext);
            knotContext.clientResponse().setHeaders(clientResponse.headers().addAll(getFilteredHeaders(clientResponse.headers(), adapterMetadata.getAllowedResponseHeaders())));
            knotContext.fragments().ifPresent(this::processFragments);
            knotContext.setTransition(DEFAULT_TRANSITION);
          }
          handler.handle(knotContext.toJson());
        },
        err -> {
          knotContext.clientResponse().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR);
          handler.handle(knotContext.toJson());
        }
    );
  }

  private void handleGetMethod(Handler<JsonObject> handler, KnotContext knotContext) {
    LOGGER.trace("Pass-through {} request", knotContext.clientRequest().method());
    knotContext.setTransition(DEFAULT_TRANSITION);
    knotContext.fragments().ifPresent(this::processFragments);
    handler.handle(knotContext.toJson());
  }

  private JsonObject prepareRequest(KnotContext knotContext, ActionKnotConfiguration.AdapterMetadata metadata) {
    ClientRequest cr = knotContext.clientRequest();
    return new JsonObject()
        .put("clientRequest", new ClientRequest()
            .setPath(cr.path())
            .setMethod(cr.method())
            .setFormAttributes(cr.formAttributes())
            .setHeaders(getFilteredHeaders(knotContext.clientRequest().headers(), metadata.getAllowedRequestHeaders())).toJson())
        .put("params", new JsonObject(metadata.getParams()));
  }

  private KnotContext processError(KnotContext context, Throwable error) {
    HttpResponseStatus statusCode;
    if (error instanceof NoSuchElementException) {
      statusCode = HttpResponseStatus.NOT_FOUND;
    } else if (error instanceof FormConfigurationException) {
      LOGGER.error("Form incorrectly configured [{}]", context.clientRequest());
      statusCode = HttpResponseStatus.INTERNAL_SERVER_ERROR;
    } else {
      statusCode = HttpResponseStatus.INTERNAL_SERVER_ERROR;
    }
    context.clientResponse().setStatusCode(statusCode);
    context.setFragments(null);
    return context;
  }

  private boolean shouldRedirect(String signal) {
    return StringUtils.isNotEmpty(signal) && !"_self".equals(signal);
  }

  private boolean isCurrentFormFragment(Fragment fragment, KnotContext knotContext) {
    return getFormIdentifier(knotContext).map(formId -> "form-" + formId).map(fragmentId -> fragmentId.equals(fragment.getId())).orElse(Boolean.FALSE);
  }

  private Optional<String> getFormIdentifier(KnotContext knotContext) {
    return Optional.ofNullable(knotContext.clientRequest().formAttributes().get(configuration.formIdentifierName()));
  }

  private void processFragments(List<Fragment> fragments) {
    fragments.stream()
        .filter(fragment -> fragment.getId().matches(ACTION_FRAGMENT_IDENTIFIER_REGEXP))
        .forEach(this::processFragment);
  }

  private void processFragment(Fragment fragment) {
    Document scriptContentDocument = getScriptContentDocument(fragment);
    Element actionFormElement = Optional.ofNullable(scriptContentDocument.getElementsByAttribute(ACTION_FORM_ACTION_ATTRIBUTE).first()).orElseThrow(() -> {
      LOGGER.error("Attribute {} not found!", ACTION_FORM_ACTION_ATTRIBUTE);
      return new FormConfigurationException(fragment);
    });
    checkActionFormNameDefinition(fragment, actionFormElement);

    LOGGER.trace("Changing fragment [{}]", fragment.getId());
    addHiddenInputTag(actionFormElement, fragment.getId());
    clearFromActionAttributes(actionFormElement);
    fragment.setContent(getFragmentContent(fragment, scriptContentDocument));
  }

  private void checkActionFormNameDefinition(Fragment fragment, Element actionFormElement) {
    String formActionName = actionFormElement.attr(ACTION_FORM_ACTION_ATTRIBUTE);
    configuration.adapterMetadatas().stream()
        .filter(adapterMetadata -> adapterMetadata.getName().equals(formActionName))
        .findFirst()
        .orElseThrow(() -> {
          LOGGER.error("Form action name [{}] not found in configuration [{}]", configuration.adapterMetadatas());
          return new FormConfigurationException(fragment);
        });
  }

  private String getFragmentContent(Fragment fragment, Document scriptContentDocument) {
    Document resultDocument = Jsoup.parse(fragment.getContent(), "UTF-8", Parser.xmlParser());
    Element scriptTag = resultDocument.child(0);
    scriptTag.children().remove();
    scriptContentDocument.children().stream().forEach(scriptTag::appendChild);

    return resultDocument.html();
  }

  private Document getScriptContentDocument(Fragment fragment) {
    Element scriptTag = Jsoup.parseBodyFragment(fragment.getContent()).body().child(0);
    return Jsoup.parse(scriptTag.unwrap().toString(), "UTF-8", Parser.xmlParser());
  }

  private void clearFromActionAttributes(Element item) {
    item.attributes().asList().stream()
        .filter(attr -> attr.getKey().matches(ACTION_FORM_ATTRIBUTES_PATTERN))
        .forEach(attr -> item.removeAttr(attr.getKey()));
  }

  private void addHiddenInputTag(Element form, String fragmentIdentifier) {
    Matcher matcher = ACTION_FRAGMENT_IDENTIFIER_PATTERN.matcher(fragmentIdentifier);
    if (matcher.find()) {
      String formIdentifier = matcher.group(1);

      Attributes attributes = Stream.of(
          new Attribute("type", "hidden"),
          new Attribute("name", configuration.formIdentifierName()),
          new Attribute("value", formIdentifier))
          .collect(Attributes::new, Attributes::put, Attributes::addAll);
      form.prependChild(new Element(Tag.valueOf("input"), "/", attributes));
    }
  }

  private MultiMap getFilteredHeaders(MultiMap headers, List<Pattern> allowedHeaders) {
    return headers.names().stream()
        .filter(AllowedHeadersFilter.create(allowedHeaders))
        .collect(MultiMapCollector.toMultimap(o -> o, headers::get));
  }

  private void traceMessage(Message<JsonObject> message) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Got message from <{}> with value <{}>", message.replyAddress(), message.body().encodePrettily());
    }
  }
}