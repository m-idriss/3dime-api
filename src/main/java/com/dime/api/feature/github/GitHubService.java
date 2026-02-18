package com.dime.api.feature.github;

import com.dime.api.feature.shared.exception.ExternalServiceException;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class GitHubService {

  @Inject
  @RestClient
  GitHubClient gitHubClient;

  @ConfigProperty(name = "github.username")
  String username;

  @ConfigProperty(name = "github.token")
  Optional<String> token;

  @Inject
  ObjectMapper objectMapper;

  @CacheResult(cacheName = "github-user-cache")
  public GitHubUser getUserInfo() {
    log.info("Fetching GitHub user info for: {}", username);

    try {
      GitHubUser user = gitHubClient.getUser(getAuthHeader().orElse(null), username);
      log.info("Successfully fetched user info for: {}", username);
      return user;
    } catch (WebApplicationException e) {
      log.error("Failed to fetch GitHub user info for: {}", username, e);
      throw new ExternalServiceException("GitHub",
          "Failed to fetch user information from GitHub API. Status: " + e.getResponse().getStatus(), e);
    } catch (Exception e) {
      log.error("Unexpected error fetching GitHub user info for: {}", username, e);
      throw new ExternalServiceException("GitHub",
          "Unexpected error occurred while calling GitHub API: " + e.getMessage(), e);
    }
  }

  @CacheResult(cacheName = "github-social-cache")
  public JsonNode getSocialAccounts() {
    try {
      return gitHubClient.getSocialAccounts(getAuthHeader().orElse(null), username);
    } catch (WebApplicationException e) {
      log.error("Failed to fetch GitHub social accounts for: {}", username, e);
      throw new ExternalServiceException("GitHub",
          "Failed to fetch social accounts from GitHub API. Status: " + e.getResponse().getStatus(), e);
    } catch (Exception e) {
      log.error("Unexpected error fetching GitHub social accounts for: {}", username, e);
      throw new ExternalServiceException("GitHub",
          "Unexpected error occurred while calling GitHub social accounts API: " + e.getMessage(), e);
    }
  }

  private Optional<String> getAuthHeader() {
    return token.filter(t -> !t.trim().isEmpty())
        .map(t -> t.startsWith("Bearer ") ? t : "Bearer " + t);
  }

  @CacheResult(cacheName = "github-commits-cache")
  public List<Map<String, Object>> getCommits(int months) {
    // Validate months parameter
    if (months < 1 || months > 60) {
      throw new IllegalArgumentException("Months parameter must be between 1 and 60");
    }

    String query = """
        query {
          user(login: "%s") {
            contributionsCollection {
              contributionCalendar {
                weeks {
                  contributionDays {
                    contributionCount
                    date
                  }
                }
              }
            }
          }
        }
        """.formatted(username);

    ObjectNode body = objectMapper.createObjectNode();
    body.put("query", query);

    // We need a token for GraphQL
    if (token.isEmpty() || token.get().trim().isEmpty()) {
      log.warn("GitHub token not configured. Skipping commit statistics fetch.");
      return new ArrayList<>();
    }

    try {
      String effectiveToken = token.get();
      String authToken = effectiveToken.startsWith("Bearer ") ? effectiveToken : "Bearer " + effectiveToken;

      JsonNode response = gitHubClient.postGraphql(authToken, body);

      if (response.has("errors")) {
        String errorMsg = response.get("errors").toString();
        log.error("GitHub GraphQL error: {}", errorMsg);
        throw new ExternalServiceException("GitHub", "GitHub GraphQL API returned errors: " + errorMsg);
      }

      List<Map<String, Object>> commits = new ArrayList<>();
      Instant cutoff = Instant.now().minus(months * 30L, ChronoUnit.DAYS);

      JsonNode weeks = response.at("/data/user/contributionsCollection/contributionCalendar/weeks");
      if (weeks.isArray()) {
        for (JsonNode week : weeks) {
          JsonNode days = week.get("contributionDays");
          if (days.isArray()) {
            for (JsonNode day : days) {
              String dateStr = day.get("date").asText();
              Instant date = Instant.parse(dateStr + "T00:00:00Z");

              if (date.isAfter(cutoff)) {
                commits.add(Map.of(
                    "date", date.toEpochMilli(),
                    "value", day.get("contributionCount").asInt()));
              }
            }
          }
        }
      }

      return commits;

    } catch (WebApplicationException e) {
      log.error("Failed to fetch GitHub commits for: {}", username, e);
      throw new ExternalServiceException("GitHub",
          "Failed to fetch commit statistics from GitHub GraphQL API. Status: " + e.getResponse().getStatus(), e);
    } catch (Exception e) {
      if (e instanceof ExternalServiceException) {
        throw e; // Re-throw our own exception
      }
      log.error("Unexpected error fetching GitHub commits for: {}", username, e);
      throw new ExternalServiceException("GitHub",
          "Unexpected error occurred while calling GitHub GraphQL API: " + e.getMessage(), e);
    }
  }
}
