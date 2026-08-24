# AI-Powered PR Review Agent

Automated GitHub Pull Request reviewer built with **Java 25**, **Spring Boot 3.4**, and **Spring AI**.  
Listens for GitHub webhooks → fetches PR diffs → runs parallel AI analysis → posts structured review comments back to GitHub.

**Deployed fully free, no credit card required.**

---

## Free Cloud Stack

| Service | Provider | Free Tier |
|---|---|---|
| App hosting | [Render](https://render.com) | 1 web service, sleeps after 15min inactivity |
| PostgreSQL | [Neon](https://neon.tech) | 0.5 GB, serverless, **never expires** |
| Redis | [Upstash](https://upstash.com) | 10,000 commands/day, **never expires** |
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
      ├── Returns 200 immediately (async processing)
      │
      ▼
ReviewOrchestrationService
      │
      ├─ Virtual Thread: analyzeFile(file, SECURITY)  ──► Groq API (free)
      ├─ Virtual Thread: analyzeFile(file, BUGS)       ──► Groq API (free)
      ├─ Virtual Thread: analyzeFile(file, PERFORMANCE)──► Groq API (free)
      └─ Virtual Thread: analyzeFile(file, CODE_QUALITY)─► Groq API (free)
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
  ai/           ← OllamaReviewAdapter (Spring AI ChatClient)
  persistence/  ← JPA entities, ReviewPersistenceAdapter

presentation/   ← Spring MVC controllers, DTOs, exception handler
  webhook/      ← GitHubWebhookController
  api/          ← ReviewController (REST history + manual trigger)
```

---

## Modern Java Features

```java
// Sealed interface — compiler-enforced exhaustive handling
public sealed interface ReviewSeverity
    permits Critical, Major, Minor, Suggestion { ... }

// Pattern matching with switch (Java 21)
String formatted = switch (severity) {
    case Critical c  -> "🚨 CRITICAL: " + c.reason();
    case Major m     -> "⚠️ MAJOR: " + m.reason();
    case Minor min   -> "💡 MINOR: " + min.reason();
    case Suggestion s -> "💭 SUGGESTION: " + s.description();
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

-----

## Deploy for Free (Step by Step)

### Step 1 — Get a free Groq API key

1. Go to [console.groq.com](https://console.groq.com)
2. Sign up (no credit card)
3. Create API key → copy it

### Step 2 — Set up Neon (free PostgreSQL)

1. Go to [neon.tech](https://neon.tech) → Sign up free
2. Create project → copy the connection string  
   Format: `postgresql://user:pass@host.neon.tech/dbname?sslmode=require`

### Step 3 — Set up Upstash (free Redis)

1. Go to [upstash.com](https://upstash.com) → Sign up free
2. Create Redis database → copy the **TLS** connection URL  
   Format: `rediss://default:token@host.upstash.io:6380`

### Step 4 — Create a GitHub Personal Access Token

1. GitHub → Settings → Developer settings → Personal access tokens → Fine-grained token
2. Permissions needed: `pull_requests: read/write`, `checks: write`
3. Copy the token

### Step 5 — Deploy to Render

1. Push this repo to GitHub
2. Go to [render.com](https://render.com) → New → Web Service
3. Connect your GitHub repo
4. Render auto-detects `render.yaml` — click **Apply**
5. In the Render dashboard, set these environment variables:

   | Key | Value |
   |---|---|
   | `DATABASE_URL` | Your Neon connection string |
   | `REDIS_URL` | Your Upstash TLS URL |
   | `REDIS_SSL_ENABLED` | `true` |
   | `GROQ_API_KEY` | Your Groq API key |
   | `GITHUB_TOKEN` | Your GitHub PAT |
   | `GITHUB_WEBHOOK_SECRET` | Any random string (e.g. `openssl rand -hex 20`) |

6. Deploy — your app URL will be: `https://pr-review-agent.onrender.com`

### Step 6 — Configure GitHub Webhook

In your target GitHub repo → Settings → Webhooks → Add webhook:

- **Payload URL**: `https://pr-review-agent.onrender.com/api/v1/webhooks/github`
- **Content type**: `application/json`
- **Secret**: same value as `GITHUB_WEBHOOK_SECRET`
- **Events**: Select "Pull requests"

**Note:** Render free tier sleeps after 15 min of inactivity. The first webhook after sleep takes ~30s to respond. GitHub will retry — the review will still be posted.

---

## Local Development

```bash
# Start local PostgreSQL + Redis
docker compose up -d

# Run with local profile (fill in application-local.yml first)
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## API Reference

### Manually trigger a review (no webhook needed)

```bash
curl -X POST https://pr-review-agent.onrender.com/api/v1/reviews/trigger \
  -H "Content-Type: application/json" \
  -d '{"owner": "your-org", "repo": "your-repo", "prNumber": 42}'
```

### Get review history for a repo

```bash
curl "https://pr-review-agent.onrender.com/api/v1/reviews?repository=owner/repo"
```

### Health check

```bash
curl https://pr-review-agent.onrender.com/actuator/health
```

---

## Sample Review Output

```json
{
  "pullRequestNumber": 42,
  "qualityScore": 4,
  "verdict": "REQUEST_CHANGES",
  "verdictDescription": "Changes requested — critical or major issues detected",
  "totalComments": 3,
  "criticalCount": 1,
  "topIssues": [
    {
      "filename": "src/main/java/UserRepository.java",
      "lineNumber": 47,
      "severity": "CRITICAL",
      "category": "SECURITY",
      "message": "SQL injection: user input concatenated directly into query string",
      "suggestion": "Use JPA parameterized queries or @Query with :param notation"
    }
  ],
  "analysisTimeMs": 2340
}
```

---

## Tech Stack

| | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 3.4 |
| AI | Spring AI 1.0 + Groq (Llama 3.3 70B) |
| Database | PostgreSQL 16 via Neon (serverless) |
| Cache | Redis 7 via Upstash (serverless) |
| HTTP Client | Spring WebFlux WebClient |
| Observability | Micrometer + Prometheus |
| Testing | JUnit 5 + Testcontainers + Mockito |
| Deploy | Render + Docker |
