# AI-Powered PR Review Agent

> A personal project by **Yash Biswakarma**

Automated GitHub Pull Request reviewer built with **Java 21**, **Spring Boot 3.4**, and **Groq AI (Llama 3.3 70B)**.  
Listens for GitHub webhooks → fetches PR diffs → runs parallel AI analysis across 4 dimensions → posts structured review comments back to GitHub.

**Deployed fully free, zero cost, no credit card required.**

Live at: `https://pr-review-agent-qh1f.onrender.com`

---

## Try It Out (Live Demo)

The app is deployed and running. To get AI reviews on your own GitHub repo:

1. **Add the webhook** to your GitHub repo → Settings → Webhooks → Add webhook:
   - **Payload URL**: `https://pr-review-agent-qh1f.onrender.com/api/v1/webhooks/github`
   - **Content type**: `application/json`
   - **Secret**: `contact me for the secret` (or deploy your own instance below)
   - **Events**: Select "Pull requests"

2. **Open a Pull Request** in that repo — the AI review will be posted automatically as a comment

3. **Check the health endpoint** anytime:
   ```bash
   curl https://pr-review-agent-qh1f.onrender.com/actuator/health
   ```

> The app runs on Render's free tier and may take ~30 seconds to wake up after inactivity. GitHub will retry the webhook automatically.

---

## What It Does

When you open or push to a Pull Request:

1. GitHub sends a webhook to the deployed app
2. The app validates the HMAC-SHA256 signature and returns `202 Accepted` immediately
3. In the background, it fetches the PR diff from GitHub
4. Runs **4 parallel AI analyses** (Security, Bugs, Performance, Code Quality) using Groq's free Llama 3.3 70B
5. Merges results, sorts by severity, calculates a quality score
6. Posts a structured review comment back to the PR with inline code annotations

---

## Free Cloud Stack

| Service | Provider | Free Tier |
|---|---|---|
| App hosting | [Render](https://render.com) | 1 web service, sleeps after 15min inactivity |
| PostgreSQL | [Neon](https://neon.tech) | 0.5 GB, serverless, never expires |
| Redis | [Upstash](https://upstash.com) | 10,000 commands/day, never expires |
| AI model | [Groq](https://groq.com) | Llama 3.3 70B, 6,000 RPM, no credit card |

---

## Architecture

```
GitHub PR opened
      │
      ▼
Render Web Service (Spring Boot)
      │
      ├── Validates HMAC-SHA256 webhook signature
      ├── Returns 202 immediately (async processing)
      │
      ▼
ReviewOrchestrationService
      │
      ├─ Virtual Thread: analyzeFile(file, SECURITY)   ──► Groq API (free)
      ├─ Virtual Thread: analyzeFile(file, BUGS)        ──► Groq API (free)
      ├─ Virtual Thread: analyzeFile(file, PERFORMANCE) ──► Groq API (free)
      └─ Virtual Thread: analyzeFile(file, CODE_QUALITY)──► Groq API (free)
                                │
                    CompletableFuture.allOf()
                                │
                                ▼
                    Merge & sort by severity
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
             Neon PostgreSQL         Upstash Redis
             (persist result)        (cache result)
                                │
                                ▼
                    POST /repos/.../pulls/.../reviews
                    (inline comments on GitHub PR)
```

### Hexagonal Architecture

```
domain/         ← pure Java records & interfaces, zero framework annotations
  model/        ← ReviewResult, PullRequest, ReviewSeverity (sealed), ReviewComment
  port/         ← GitHubPort, AiReviewPort, ReviewPersistencePort (interfaces)
  service/      ← ReviewOrchestrationService (parallel analysis logic)

application/    ← Spring @Service use cases wiring ports together
  usecase/      ← ProcessWebhookUseCase, TriggerManualReviewUseCase
  event/        ← PrReceivedEvent, ReviewCompletedEvent

infrastructure/ ← implements the ports
  github/       ← GitHubAdapter (WebClient), WebhookSignatureVerifier
  ai/           ← OllamaReviewAdapter, GroqClient, PromptBuilder
  persistence/  ← JPA entities, ReviewPersistenceAdapter

presentation/   ← Spring MVC controllers, DTOs, exception handler
  webhook/      ← GitHubWebhookController
  api/          ← ReviewController (REST history + manual trigger)
```

---

## Modern Java Features Used

```java
// Sealed interface — compiler-enforced exhaustive handling
public sealed interface ReviewSeverity
    permits Critical, Major, Minor, Suggestion { ... }

// Pattern matching with switch (Java 21)
String formatted = switch (severity) {
    case Critical c   -> "CRITICAL: " + c.reason();
    case Major m      -> "MAJOR: " + m.reason();
    case Minor min    -> "MINOR: " + min.reason();
    case Suggestion s -> "SUGGESTION: " + s.description();
};

// Virtual threads — each file×dimension runs as a separate virtual thread
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    var futures = files.stream()
        .flatMap(file -> DIMENSIONS.stream()
            .map(dim -> CompletableFuture.supplyAsync(
                () -> ai.analyzeFile(file, dim), executor)))
        .toList();
    futures.forEach(CompletableFuture::join);
}

// Records — immutable, thread-safe domain model
public record ReviewResult(
    String reviewId, int pullRequestNumber,
    List<ReviewComment> comments, int qualityScore,
    ReviewVerdict verdict, Instant reviewedAt, ...
) {}
```

---

## Deploy for Free (Step by Step)

### Step 1 — Get a free Groq API key

1. Go to [console.groq.com](https://console.groq.com)
2. Sign up (no credit card)
3. Create API key → copy it

### Step 2 — Set up Neon (free PostgreSQL)

1. Go to [neon.tech](https://neon.tech) → Sign up free
2. Create project → copy the connection string
3. Split into 3 parts for the env vars below (host only in `DB_URL`, credentials separate)

### Step 3 — Set up Upstash (free Redis)

1. Go to [upstash.com](https://upstash.com) → Sign up free
2. Create Redis database → copy the **TLS** connection URL from the TCP tab
   Format: `rediss://default:token@host.upstash.io:6380`

### Step 4 — Create a GitHub Personal Access Token

1. GitHub → Settings → Developer settings → Personal access tokens → Fine-grained token
2. Permissions needed: `pull_requests: read/write`, `checks: write`
3. Copy the token

### Step 5 — Deploy to Render

1. Push this repo to GitHub
2. Go to [render.com](https://render.com) → New → Web Service
3. Connect your GitHub repo → Render auto-detects the `Dockerfile`
4. Set these environment variables:

   | Key | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://host.neon.tech/dbname?sslmode=require` |
   | `DB_USERNAME` | Your Neon username |
   | `DB_PASSWORD` | Your Neon password |
   | `REDIS_URL` | Your Upstash TLS URL (`rediss://...`) |
   | `REDIS_SSL_ENABLED` | `true` |
   | `GROQ_API_KEY` | Your Groq API key |
   | `GITHUB_TOKEN` | Your GitHub PAT |
   | `GITHUB_WEBHOOK_SECRET` | Any random string (e.g. `openssl rand -hex 20`) |

5. Deploy — your app URL will be: `https://<your-service>.onrender.com`

### Step 6 — Configure GitHub Webhook

In your target GitHub repo → Settings → Webhooks → Add webhook:

- **Payload URL**: `https://<your-service>.onrender.com/api/v1/webhooks/github`
- **Content type**: `application/json`
- **Secret**: same value as `GITHUB_WEBHOOK_SECRET`
- **Events**: Select "Pull requests"

> **Note:** Render free tier sleeps after 15 min of inactivity. Set up [UptimeRobot](https://uptimerobot.com) (free) to ping `/actuator/health` every 5 minutes to keep it warm.

---

## Local Development

```bash
# Start local PostgreSQL + Redis
docker compose up -d

# Run with local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## API Reference

### Manually trigger a review (no webhook needed)

```bash
curl -X POST https://<your-service>.onrender.com/api/v1/reviews/trigger \
  -H "Content-Type: application/json" \
  -d '{"owner": "your-org", "repo": "your-repo", "prNumber": 42}'
```

### Get review history for a repo

```bash
curl "https://<your-service>.onrender.com/api/v1/reviews?repository=owner/repo"
```

### Health check

```bash
curl https://<your-service>.onrender.com/actuator/health
```

---

## Sample Review Output

```
## AI Code Review

**Quality Score:** 4/10
**Verdict:** Changes requested — critical or major issues detected

### Summary
This PR introduces a user search endpoint but has a critical SQL injection
vulnerability and missing pagination for large datasets.

### Critical & Major Issues

- **UserRepository.java** (`SECURITY`): SQL injection — user input concatenated directly into query string
- **UserService.java** (`PERFORMANCE`): Missing pagination — query could return unbounded rows

### Issue Breakdown

| Category | Count |
|---|---|
| SECURITY | 1 |
| PERFORMANCE | 1 |
| CODE_QUALITY | 2 |

---
*Reviewed by AI PR Review Agent*
```

---

## Tech Stack

| | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4 |
| AI | Groq REST API (Llama 3.3 70B) |
| Database | PostgreSQL 16 via Neon (serverless) |
| Cache | Redis 7 via Upstash (serverless) |
| HTTP Client | Spring WebFlux WebClient |
| Observability | Micrometer + Prometheus |
| Testing | JUnit 5 + Mockito |
| Deploy | Render + Docker |
