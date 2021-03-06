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
package io.knotx.adapter.common.http;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ServiceMetadata {

  private String path;
  private String domain;
  private Integer port;
  private List<Pattern> allowedRequestHeaderPatterns;
  private JsonObject queryParams;
  private JsonObject additionalHeaders;

  public String getPath() {
    return path;
  }

  public ServiceMetadata setPath(String path) {
    this.path = path;
    return this;
  }

  public String getDomain() {
    return domain;
  }

  public ServiceMetadata setDomain(String domain) {
    this.domain = domain;
    return this;
  }

  public Integer getPort() {
    return port;
  }

  public ServiceMetadata setPort(Integer port) {
    this.port = port;
    return this;
  }

  public List<Pattern> getAllowedRequestHeaderPatterns() {
    return allowedRequestHeaderPatterns;
  }

  public ServiceMetadata setAllowedRequestHeaderPatterns(
      List<Pattern> allowedRequestHeaderPatterns) {
    this.allowedRequestHeaderPatterns = allowedRequestHeaderPatterns;
    return this;
  }

  public Optional<JsonObject> getQueryParams() {
    return Optional.ofNullable(queryParams);
  }

  public void setQueryParams(JsonObject queryParams) {
    this.queryParams = queryParams;
  }

  public Optional<JsonObject> getAdditionalHeaders() {
    return Optional.ofNullable(additionalHeaders);
  }

  public void setAdditionalHeaders(JsonObject additionalHeaders) {
    this.additionalHeaders = additionalHeaders;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null && obj instanceof ServiceMetadata) {
      final ServiceMetadata other = (ServiceMetadata) obj;
      return new EqualsBuilder()
          .append(path, other.getPath())
          .append(domain, other.getDomain())
          .append(port, other.getPort()).isEquals();
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(path)
        .append(domain)
        .append(port)
        .toHashCode();
  }
}
