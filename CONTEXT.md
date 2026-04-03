# CareerBridge AI — Project context (backend + repo layout)

This document is the **single reference** for resuming work on **CareerBridge AI**.  
**Active backend module:** `backend/career-transition-platform` (Spring Boot **3.5.13**, Java **17**).  
**Frontend:** described in `readme.md` at repo root — **not present in this repository** as application code.

**Package base (Java):** `com.ai.guild.career_transition_platform`

---

## 1. Repository layout (root)

```
CareerBridge AI/
├── .gitignore                 # ignores .env and **/.env
├── .env                       # local secrets (not committed)
├── .env.example               # template / hints for env vars (do not commit real secrets)
├── CONTEXT.md                 # this file
├── readme.md                  # product vision, features, target stack
├── .vscode/
│   └── settings.json
└── backend/
    └── career-transition-platform/
        ├── pom.xml
        ├── mvnw, mvnw.cmd
        ├── .mvn/
        ├── .env                 # optional; spring-dotenv may load from module dir (${user.dir})
        └── src/
            ├── main/
            │   ├── java/com/ai/guild/career_transition_platform/
            │   │   ├── CareerTransitionPlatformApplication.java
            │   │   ├── config/          # LangChain4jConfig, MinioConfig, OkHttpClientConfig
            │   │   ├── controller/      # Auth, Profile, Interview, PostInterview
            │   │   ├── dto/
            │   │   ├── entity/
            │   │   ├── enums/
            │   │   ├── exception/
            │   │   ├── repository/
            │   │   ├── security/
            │   │   └── service/
            │   └── resources/
            │       └── application.properties
            └── test/
                ├── java/.../CareerTransitionPlatformApplicationTests.java
                └── resources/application.properties
```

**Build output:** `backend/career-transition-platform/target/` (do not version).

---

## 2. Entities (JPA)

| Entity | Table | Attributes | Relationships |
|--------|--------|------------|----------------|
| **User** | `users` | `id` (PK), `email` (unique), `passwordHash`, `firstName`, `lastName`, `imagePath` (nullable, MinIO object key), `role` (`Role`, default `USER`), `createdAt`, `updatedAt` | OneToMany: `Interview`, `Recommendation`, `Formation`, `Candidature`, `Message` (all mapped by `user`, cascade ALL, orphanRemoval where defined) |
| **Interview** | `interviews` | `id`, `transcript` (TEXT, JSON array of `{role, content}`), `skillAnalysis` (TEXT, JSON from Mistral), `startedAt`, `completedAt`, `createdAt`, `updatedAt` | ManyToOne `User`; OneToMany `Recommendation` (cascade ALL, orphanRemoval) |
| **Recommendation** | `recommendations` | `id`, `sector`, `jobTitle`, `description` (TEXT), `rankOrder`, `createdAt`, `updatedAt` | ManyToOne `User`, `Interview` (required); OneToMany `Formation` |
| **Formation** | `formations` | `id`, `sector`, `title`, `description` (TEXT), `selectedAt`, `createdAt`, `updatedAt` | ManyToOne `User` (required); ManyToOne `Recommendation` (nullable FK) |
| **Candidature** | `candidatures` | `id`, `companyName`, `jobTitle`, `status` (`CandidatureStatus`, default `PENDING`), `responseMessage` (TEXT), `appliedAt`, `updatedAt` | ManyToOne `User` (required) |
| **Message** | `messages` | `id`, `context` (String, max 100), `orderIndex`, `body` (TEXT), `createdAt` | ManyToOne `User` (required) |

**Enums**

- `Role`: `USER`, `ADMIN`
- `CandidatureStatus`: `PENDING`, `ACCEPTED`, `REJECTED`

**Message `body` encoding (important):** the app does **not** use separate DB columns for sender/type. `MessageService` stores JSON in `body`:

```json
{"sender":"USER|BOT","type":"TEXT|...","content":"<payload string>"}
```

`content` may itself be a JSON string (e.g. Mistral post-interview assistant JSON, or candidature UI payload).

**Message `context` conventions (string keys, not enums)**

| Pattern | Usage |
|---------|--------|
| `INTERVIEW_{interviewId}` | Interview chat thread for that interview |
| `ANALYSIS_{interviewId}` | Skill analysis summary message after `completeInterview` |
| `POST_INTERVIEW_{interviewId}` | Post-interview guided flow messages |
| `CANDIDATURE` | In-app notifications for candidature ACCEPTED/REJECTED (`EmailService`) |

---

## 3. Repositories

All extend `JpaRepository<Entity, Long>` unless noted.

| Repository | Methods |
|------------|---------|
| **UserRepository** | `findByEmail(String)`, `existsByEmail(String)` |
| **InterviewRepository** | `findByUser_IdOrderByCreatedAtDesc(Long)`, `findFirstByUser_IdAndCompletedAtIsNotNullOrderByCompletedAtDesc(Long)`, `findFirstByUser_IdAndCompletedAtIsNullOrderByStartedAtDesc(Long)`, `findByIdAndUser_Id(Long, Long)` |
| **RecommendationRepository** | `findByUser_Id(Long)`, `findByInterview_Id(Long)`, `findByUser_IdOrderByRankOrderAsc(Long)` |
| **FormationRepository** | `findByUser_Id(Long)`, `findByRecommendation_Id(Long)` |
| **CandidatureRepository** | `findByUser_Id(Long)`, `findByUser_IdAndStatus(Long, CandidatureStatus)` |
| **MessageRepository** | `findByUser_IdAndContextOrderByOrderIndexAsc(Long, String)`; `@Query` `findMaxOrderIndexByUserIdAndContext` → `COALESCE(MAX(orderIndex), -1)` |

---

## 4. Services

### AuthService

| Method | Description |
|--------|-------------|
| `register(RegisterRequest)` | Ensures email unique; BCrypt password; role `USER`; loads `UserDetails`, returns `AuthResponse` with JWT. Throws `EmailAlreadyExistsException` if email exists. |
| `login(LoginRequest)` | `AuthenticationManager` with email/password; on success loads `User`, JWT via `JwtService`; throws `BadCredentialsException` on failure. |

### ProfileService

| Method | Description |
|--------|-------------|
| `getProfile(Long userId)` | Maps `User` → `ProfileResponse`. |
| `updateProfile(Long, UpdateProfileRequest)` | Updates first/last name. |
| `changePassword(Long, ChangePasswordRequest)` | Verifies current password; throws `InvalidPasswordException` if mismatch. |
| `uploadProfilePhoto(Long, MultipartFile)` | Stores object in MinIO `profile_{userId}_{timestamp}.{ext}`; removes previous object if any. |
| `getProfilePhoto(Long userId)` | `Optional<ProfilePhotoContent>` (`record`: `byte[] data`, `String contentType`). |

### MessageService

| Method | Description |
|--------|-------------|
| `saveMessage(Long userId, String context, String sender, String content, String type)` | Next `orderIndex` via `findMaxOrderIndexByUserIdAndContext`; persists wrapped JSON in `body`. |
| `getMessages(Long userId, String context)` | Ordered by `orderIndex` ASC. |
| `getMessagesAsDto(...)` | Same, mapped to `MessageDto` (parses inner JSON for sender/type/content). |

### SpeechService

| Method | Description |
|--------|-------------|
| `transcribe(byte[] audioBytes, String filename, String contentType)` | **STT chain:** Groq Whisper (`whisper-large-v3`) → AssemblyAI upload/poll → `null` (signal browser Web Speech API). |
| `synthesize(String text)` | **TTS chain:** ElevenLabs → Unreal Speech stream → `null` (browser TTS). Returns `SpeechAudio` record (`audioBytes`, `contentType`) or null. |

### InterviewService

Constants: `SENDER_USER`, `SENDER_BOT`, `TYPE_TEXT`.

| Method | Description |
|--------|-------------|
| `startInterview(Long userId)` | Creates `Interview` with `transcript="[]"`, `startedAt=now`; Mistral intro + first question; persists transcript JSON; saves BOT message under `INTERVIEW_{id}`; TTS for intro → `InterviewStartResponse`. |
| `respondToInterview(Long userId, Long interviewId, String userTranscribedText)` | Loads interview; builds LangChain4j messages from transcript + system prompt; appends user turn; Mistral reply; updates transcript; may set `completedAt` if ≥7 user turns or closing phrase; saves USER+BOT messages; TTS → `InterviewRespondResponse` (`isCompleted`, `browserSttRequired`). |
| `completeInterview(Long userId, Long interviewId)` | Requires `completedAt` set; Mistral produces skill-analysis JSON; saves `skillAnalysis`; replaces recommendations for interview; saves message under `ANALYSIS_{id}`. |
| `getCurrentIncompleteInterview(Long userId)` | Latest interview with `completedAt == null`, or `null`. |

### EmailService

| Method | Description |
|--------|-------------|
| `sendFormationInscriptionEmail(...)` | SMTP to fixed notification inbox; subject `[CareerBridge] Inscription Formation — {title}`; body includes simulated formation account password `CBG_XXXXXX`. |
| `sendCandidatureEmail(Long userId, ...)` | Creates `Candidature` **PENDING**; SMTP with instructions; returns `candidatureId`. |
| `startImapPolling()` | `@Scheduled(fixedDelay = 30000)`; if `mail.imap.polling.enabled` true: IMAP unread + subject contains `[CareerBridge-Response]`; parses `ACCEPTED_{id}` / `REJECTED_{id}`; calls `self.processCandidatureResponse` (lazy self for `@Transactional`). |
| `processCandidatureResponse(Long candidatureId, String status)` | Updates status; saves `Message` under context `CANDIDATURE` with JSON content for ACCEPTED vs REJECTED. |

### PostInterviewService

| Method | Description |
|--------|-------------|
| `startPostInterviewFlow(Long userId, Long interviewId)` | Requires completed interview + non-empty `skillAnalysis`; loads recommendations; Mistral JSON assistant message; saves under `POST_INTERVIEW_{id}`; returns `PostInterviewResponse`. |
| `processUserChoice(...)` | Saves USER message; branches on `context` string (e.g. `ANALYSIS_RESPONSE`, `SECTOR_CHOICE`, `FORMATION_CHOICE`, `INSCRIPTION_CONFIRMED`, `APPLICATION_AI`, …); may call `EmailService`; saves BOT message; returns `PostInterviewResponse`. |
| `postInterviewContext(Long interviewId)` | **static** → `"POST_INTERVIEW_" + interviewId`. |

---

## 5. Controllers and HTTP API

**Global:** Except auth, endpoints expect header `Authorization: Bearer <JWT>`.  
**CORS:** `http://localhost:3000` on controllers + global `SecurityConfig` CORS.

### AuthController — `@RequestMapping("/api/auth")`

| Method | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| POST | `/api/auth/register` | `RegisterRequest` JSON | `AuthResponse` | **201** |
| POST | `/api/auth/login` | `LoginRequest` JSON | `AuthResponse` | **200** |

### ProfileController — `@RequestMapping("/api/profile")`

| Method | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| GET | `/api/profile` | — | `ProfileResponse` | **200** |
| PUT | `/api/profile` | `UpdateProfileRequest` | `ProfileResponse` | **200** |
| PUT | `/api/profile/password` | `ChangePasswordRequest` | empty | **200** |
| POST | `/api/profile/photo` | multipart `file` | `ProfileResponse` | **200** |
| GET | `/api/profile/photo` | — | raw image bytes + `Content-Type` | **200** or **404** |

### InterviewController — `@RequestMapping("/api/interview")`

| Method | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| POST | `/api/interview/start` | — | `InterviewStartResponse` | **201** |
| POST | `/api/interview/{interviewId}/respond` | multipart: `audio` and/or `text` | `InterviewRespondResponse` | **200**; STT failure → `browserSttRequired=true`; missing audio+text → **400** (exception); read error → **500** |
| GET | `/api/interview/{interviewId}/messages` | — | `List<MessageDto>` | **200** |
| GET | `/api/interview/current` | — | `InterviewCurrentResponse` (nullable ids) | **200** |
| POST | `/api/interview/{interviewId}/complete` | — | empty | **204** |

### PostInterviewController — `@RequestMapping("/api")`

| Method | Path | Request | Response | Status |
|--------|------|---------|----------|--------|
| POST | `/api/post-interview/start` | `PostInterviewStartRequest` | `PostInterviewResponse` | **201** |
| POST | `/api/post-interview/choice` | `UserChoiceRequest` | `PostInterviewResponse` | **200** |
| GET | `/api/post-interview/{interviewId}/messages` | — | `List<MessageDto>` | **200** |
| POST | `/api/admin/candidature/respond` | `CandidatureResponseRequest` | empty | **200** if `Role.ADMIN`; **403** otherwise |

---

## 6. DTOs (fields)

| DTO | Fields |
|-----|--------|
| **RegisterRequest** | `firstName`, `lastName`, `email`, `password` |
| **LoginRequest** | `email`, `password` |
| **AuthResponse** | `token`, `email`, `firstName`, `lastName`, `role` |
| **ProfileResponse** | `id`, `email`, `firstName`, `lastName`, `imagePath` |
| **UpdateProfileRequest** | `firstName`, `lastName` |
| **ChangePasswordRequest** | `currentPassword`, `newPassword` |
| **InterviewStartResponse** | `interviewId`, `introductionText`, `audioBytes`, `audioContentType` |
| **InterviewRespondResponse** | `aiResponseText`, `audioBytes`, `audioContentType`, `isCompleted` (JSON name), `browserSttRequired` |
| **InterviewCurrentResponse** | `interviewId`, `startedAt` |
| **TranscribeRequest** | `interviewId` (optional helper DTO; not wired to a dedicated endpoint) |
| **MessageDto** | `id`, `orderIndex`, `sender`, `type`, `content`, `createdAt` |
| **PostInterviewResponse** | `message`, `options` (list), `type`, `data` (`JsonNode`) |
| **PostInterviewStartRequest** | `interviewId` |
| **UserChoiceRequest** | `interviewId`, `choice`, `context` |
| **CandidatureResponseRequest** | `candidatureId`, `status` (`ACCEPTED` / `REJECTED`) |

---

## 7. Security and JWT

### Classes

- **SecurityConfig** — Stateless sessions, CSRF off, JWT filter before `UsernamePasswordAuthenticationFilter`; public: `OPTIONS /**`, `POST /api/auth/register`, `POST /api/auth/login`; all other requests **authenticated**. CORS bean: origin `http://localhost:3000`, methods including OPTIONS, all headers, credentials true.
- **JwtService** — HS256 via `jjwt`; `jwt.secret` → `Keys.hmacShaKeyFor`; claims: subject = email, issuedAt, expiration from `jwt.expiration` (ms); `generateToken`, `extractUsername`, `isTokenValid` (subject match + expiry).
- **JwtAuthFilter** — Reads `Authorization: Bearer <token>`; loads `UserDetails` by email; validates token; sets `SecurityContext` with `ROLE_<Role>`.
- **UserDetailsServiceImpl** — Loads `User` by email; Spring `User` with `ROLE_USER` or `ROLE_ADMIN`.

### JWT usage

1. Login/register returns JWT in `AuthResponse.token`.
2. Client sends `Authorization: Bearer <token>` on protected routes.
3. Filter sets authentication; controllers use `SecurityContextHolder` + `UserRepository.findByEmail` for domain `User` where needed.

**Note:** `UsernameNotFoundException` from controllers is **not** mapped in `GlobalExceptionHandler` (may surface as 500 unless Spring MVC handles it).

---

## 8. Config classes

| Class | Purpose |
|-------|---------|
| **LangChain4jConfig** | `@Bean ChatModel` — `MistralAiChatModel`, `MISTRAL_SMALL_LATEST`, `mistral.api-key`. |
| **OkHttpClientConfig** | `@Bean OkHttpClient` — long timeouts for speech APIs. |
| **MinioConfig** | `@Bean MinioClient`; `@Bean MinioBucketInitializer` — optional create bucket when `minio.verify-bucket-at-startup=true`. |

**Scheduling:** `@EnableScheduling` on `CareerTransitionPlatformApplication` (IMAP polling).

---

## 9. Exceptions and handling

| Exception | HTTP | Handler |
|-----------|------|---------|
| **InterviewNotFoundException** | 404 | `GlobalExceptionHandler` |
| **CandidatureNotFoundException** | 404 | `CandidatureExceptionHandler` |
| **EmailAlreadyExistsException** | 409 | `GlobalExceptionHandler` |
| **BadCredentialsException** | 401 | `GlobalExceptionHandler` |
| **InvalidPasswordException** | 400 | `GlobalExceptionHandler` |
| **IllegalArgumentException** | 400 | `GlobalExceptionHandler` |
| **IllegalStateException** | 400 | `GlobalExceptionHandler` |

Other runtime exceptions (e.g. `EntityNotFoundException` from `ProfileService`) may not be mapped → default Spring error handling.

---

## 10. `application.properties` keys (main)

**Application:** `spring.application.name`, `server.port`

**Dotenv:** `springdotenv.directory` (default `${user.dir}`)

**JWT:** `jwt.secret`, `jwt.expiration`

**AI / speech:** `mistral.api-key`, `groq.api-key`, `assemblyai.api-key`, `elevenlabs.api-key`, `elevenlabs.voice-id`, `unreal-speech.api-key`

**Mail:** `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password`, `spring.mail.properties.mail.smtp.auth`, `spring.mail.properties.mail.smtp.starttls.enable`, `mail.imap.host`, `mail.imap.port`, `mail.imap.username`, `mail.imap.password`, `mail.imap.polling.enabled`

**MinIO:** `minio.url`, `minio.access-key`, `minio.secret-key`, `minio.bucket-name`, `minio.verify-bucket-at-startup`

**Multipart:** `spring.servlet.multipart.max-file-size`, `max-request-size`

**Datasource:** `spring.datasource.url`, `username`, `password`, `driver-class-name`

**JPA:** `spring.jpa.hibernate.ddl-auto`, `show-sql`, `format_sql`, `open-in-view`, `hibernate.jdbc.time_zone`

**Logging:** `logging.level.root`, `logging.level.com.ai.guild.career_transition_platform`, `logging.pattern.console`

---

## 11. Environment variables (names only — no values)

Set via OS env or `.env` (loaded by spring-dotenv from configured directory). **Do not commit real secrets.**

| Variable | Used for |
|----------|----------|
| `SPRINGDOTENV_DIRECTORY` | Optional override for folder containing `.env` |
| `JWT_SECRET` | HMAC key for JWT (must be sufficient length for HS256) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `MISTRAL_API_KEY` | Mistral (LangChain4j) |
| `GROQ_API_KEY` | Groq Whisper STT |
| `ASSEMBLYAI_API_KEY` | AssemblyAI STT fallback |
| `ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID` | ElevenLabs TTS |
| `UNREAL_SPEECH_API_KEY` | Unreal Speech TTS fallback |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail SMTP + IMAP (use App Password for Gmail) |
| `MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_NAME` | MinIO |
| `MINIO_VERIFY_BUCKET` | Optional boolean for bucket init |

**Reminder:** keep `.env` out of git; rotate any key that was ever committed to `.env.example` by mistake.

---

## 12. Maven dependencies (high level)

- Spring Boot: Web, Data JPA, Security, Validation, Mail, Devtools, Test  
- `me.paulschwarz:spring-dotenv:4.0.0`  
- PostgreSQL (runtime), H2 (test)  
- jjwt 0.12.5 (api, impl, jackson)  
- MinIO 8.5.7  
- LangChain4j 1.12.2 + langchain4j-mistral-ai  
- OkHttp 4.12.0  
- Lombok, spring-boot-configuration-processor  

`jackson-databind` is provided transitively via `spring-boot-starter-web`.

---

## 13. What is implemented vs remaining

### Implemented (backend)

- Auth (register/login, JWT), profile CRUD, password, MinIO profile photo  
- Interview flow: Mistral voice interview, transcript JSON, TTS/STT chains, messages per interview, `completeInterview` skill analysis + recommendations + analysis message  
- Post-interview JSON flow (`PostInterviewService`) + related endpoints  
- Email: formation + candidature notifications; IMAP polling + admin REST fallback for candidature response  
- `MessageService` inbox-style storage with context strings  
- Global + candidature exception handlers for listed exceptions  
- CORS for React dev origin  

### Not in this repo / not done

- **React frontend** (Web Speech API, UI, admin UI)  
- **Strict Bean Validation** on DTOs (`@Valid` not used in controllers)  
- **Dedicated REST** for formations list/create, candidature list, generic inbox aggregation (only context-based message reads exist)  
- **CI/CD**, production hardening (CORS, secrets rotation, rate limits)  
- **Integration tests** beyond `contextLoads`  
- **Role-based security at URL level** for `/api/admin/*` (admin endpoint checks `Role` in controller only)

---

## 14. Project conventions

| Topic | Convention |
|-------|------------|
| **Package** | `com.ai.guild.career_transition_platform` |
| **Layers** | Controller → Service → Repository; entities not exposed directly in API (DTOs) |
| **Logging** | `@Slf4j`; `log.info` success/flow, `warn` business/security oddities, `error` failures, `debug` verbose (tokens, payloads) |
| **CORS** | `http://localhost:3000` on controllers + `SecurityConfig` |
| **Validation** | `spring-boot-starter-validation` on classpath; **no `@Valid` on controller parameters** in current code — validate in service or add later |
| **CrossOrigin** | Present on REST controllers that serve the SPA |
| **i18n** | User-facing API copy mixed EN/FR in emails and some messages; code identifiers in English |
| **Secrets** | `.gitignore` excludes `.env` and `**/.env` |

---

## 15. Tests

- `CareerTransitionPlatformApplicationTests` — `@SpringBootTest`, `contextLoads()` only.  
- Test `application.properties`: H2, `springdotenv.enabled=false`, dummy AI/mail/minio keys, **`mail.imap.polling.enabled=false`**.

---

## 16. Useful commands

From `backend/career-transition-platform`:

```bash
./mvnw test
./mvnw spring-boot:run
```

(Windows: `mvnw.cmd`.)

---

*Generated from the codebase structure and source files in this repository. Update this file when adding packages, endpoints, or env vars.*
