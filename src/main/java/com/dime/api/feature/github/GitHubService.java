package com.dime.api.feature.github;

import com.dime.api.feature.shared.FirestoreCacheService;
import com.dime.api.feature.shared.exception.ExternalServiceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
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

  @Inject
  FirestoreCacheService firestoreCacheService;

  LoadingCache<String, GitHubUser> userCache;
  LoadingCache<String, JsonNode> socialCache;
  LoadingCache<Integer, List<Map<String, Object>>> commitsCache;

  @PostConstruct
  void initCaches() {
    userCache = Caffeine.newBuilder()
        .refreshAfterWrite(Duration.ofHours(1))
        .expireAfterWrite(Duration.ofHours(24))
        .build(key -> fetchUser());

    socialCache = Caffeine.newBuilder()
        .refreshAfterWrite(Duration.ofHours(1))
        .expireAfterWrite(Duration.ofHours(24))
        .build(key -> fetchSocial());

    commitsCache = Caffeine.newBuilder()
        .refreshAfterWrite(Duration.ofHours(1))
        .expireAfterWrite(Duration.ofHours(24))
        .build(this::fetchCommits);
  }

  public GitHubUser getUserInfo() {
    return userCache.get("default");
  }

  public JsonNode getSocialAccounts() {
    return socialCache.get("default");
  }

  public List<Map<String, Object>> getCommits(int months) {
    if (months < 1 || months > 60) {
      throw new IllegalArgumentException("Months parameter must be between 1 and 60");
    }
    return commitsCache.get(months);
  }

  public void warmFromFirestore() {
    if (firestoreCacheService == null) return;
    firestoreCacheService.read("github-user", GitHubUser.class)
        .ifPresent(user -> userCache.put("default", user));
    firestoreCacheService.read("github-social", JsonNode.class)
        .ifPresent(social -> socialCache.put("default", social));
    firestoreCacheService.read("github-commits-12", new TypeReference<List<Map<String, Object>>>() {})
        .ifPresent(commits -> commitsCache.put(12, commits));
  }

  private Optional<String> getAuthHeader() {
    return token.filter(t -> !t.trim().isEmpty())
        .map(t -> t.startsWith("Bearer ") ? t : "Bearer " + t);
  }

  private GitHubUser fetchUser() {
    log.info("Fetching GitHub user info for: {}", username);
    try {
      GitHubUser user = gitHubClient.getUser(getAuthHeader().orElse(null), username);
      log.info("Successfully fetched user info for: {}", username);
      if (firestoreCacheService != null) firestoreCacheService.write("github-user", user);
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

  private JsonNode fetchSocial() {
    log.info("Fetching GitHub social accounts for: {}", username);
    try {
      JsonNode social = gitHubClient.getSocialAccounts(getAuthHeader().orElse(null), username);
      if (firestoreCacheService != null) firestoreCacheService.write("github-social", social);
      return social;
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

  private List<Map<String, Object>> fetchCommits(int months) {
    log.info("Fetching GitHub commits for: {} (months: {})", username, months);

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

      if (firestoreCacheService != null) firestoreCacheService.write("github-commits-" + months, commits);
      return commits;

    } catch (WebApplicationException e) {
      log.error("Failed to fetch GitHub commits for: {}", username, e);
      throw new ExternalServiceException("GitHub",
          "Failed to fetch commit statistics from GitHub GraphQL API. Status: " + e.getResponse().getStatus(), e);
    } catch (Exception e) {
      if (e instanceof ExternalServiceException) {
        throw e;
      }
      log.error("Unexpected error fetching GitHub commits for: {}", username, e);
      throw new ExternalServiceException("GitHub",
          "Unexpected error occurred while calling GitHub GraphQL API: " + e.getMessage(), e);
    }
  }
}
