package com.threedime.api.service;

import com.threedime.api.client.GitHubClient;
import com.threedime.api.client.GitHubUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class GitHubService {

  private static final Logger LOG = Logger.getLogger(GitHubService.class);

  @Inject
  @RestClient
  GitHubClient gitHubClient;

  @ConfigProperty(name = "github.username")
  String username;

  @ConfigProperty(name = "github.token")
  Optional<String> token;

  @Inject
  ObjectMapper objectMapper;

  public GitHubUser getUserInfo() {
    LOG.infof("Fetching GitHub user info for: %s", username);

    try {
      GitHubUser user = gitHubClient.getUser(username);
      LOG.infof("Successfully fetched user info for: %s", username);
      return user;
    } catch (WebApplicationException e) {
      LOG.errorf(e, "Failed to fetch GitHub user info for: %s", username);
      // Return 502 Bad Gateway for all external API failures
      throw new WebApplicationException("Failed to fetch user from GitHub API",
          Response.Status.BAD_GATEWAY);
    } catch (Exception e) {
      LOG.errorf(e, "Unexpected error fetching GitHub user info for: %s", username);
      throw new WebApplicationException("Unexpected error calling GitHub API",
          Response.Status.BAD_GATEWAY);
    }
  }

  public JsonNode getSocialAccounts() {
    return gitHubClient.getSocialAccounts(username);
  }

  public List<Map<String, Object>> getCommits(int months) {
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
    String effectiveToken = token.orElse("");
    String authToken = effectiveToken.startsWith("Bearer ") ? effectiveToken : "Bearer " + effectiveToken;

    JsonNode response = gitHubClient.postGraphql(authToken, body);

    if (response.has("errors")) {
      throw new RuntimeException("GitHub GraphQL error: " + response.get("errors").toString());
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
  }
}
