# CareerBridge AI — contexte projet (état au 2026-04-04)

Document de synthèse pour les assistants et développeurs : structure réelle du dépôt, stack, flux métier et configuration. Complète le `readme.md` (vision produit) par l’état technique actuel.

---

## 1. Vue d’ensemble

- **Nom / concept** : plateforme de transition de carrière assistée par IA (« Future Skills Guild » / The Forge Guild), pour travailleurs impactés par l’automatisation.
- **Code dans le dépôt** :
  - **Backend** : Spring Boot dans `backend/career-transition-platform/`.
  - **Frontend** : application **React 19** + **Vite 8** dans `frontend/` (`package.json`, sources sous `frontend/src/`). Client HTTP **axios** vers `http://localhost:8080`, JWT stocké en `localStorage` (`careerbridge_token` / `careerbridge_user`).
- **Racine du dépôt** : fichiers HTML statiques hérités ou maquettes (`index.html`, `dashboard.html`, `registre.html`, `interview.html`, images `*.jpg` / `*.png`) — le parcours applicatif principal cible le **frontend Vite**.
- **Package Java** : `com.ai.guild.career_transition_platform` (le nom Maven avec tirets n’est pas valide en Java ; voir `HELP.md`).

---

## 2. Frontend — stack et structure

| Élément | Détail |
|--------|--------|
| Build | Vite **8**, plugin `@vitejs/plugin-react` |
| UI | **React 19**, **React Router 7**, **Tailwind CSS 4** (`index.css`, PostCSS) |
| Icônes | `lucide-react` |
| État auth | `AuthContext` (reducer + `localStorage`) — la dépendance **zustand** est listée dans `package.json` mais **non utilisée** dans le code actuel |
| API | `src/api/api.js` : instance axios, intercepteur Bearer + redirection `/login` sur 401 |

**Routes** (`App.jsx`) :

| Chemin | Comportement |
|--------|----------------|
| `/` | `HomePage` (landing marketing) |
| `/login`, `/register` | `PublicOnlyRoute` — redirige vers `/dashboard` si déjà connecté |
| `/dashboard`, `/interview`, `/messages` | `ProtectedRoute` — JWT requis |
| `*` | redirection vers `/` |

**Pages** :

- **HomePage** : landing (assets dans `src/assets/`, dont `download.png`, `robot.png`, etc.).
- **LoginPage / RegisterPage** : formulaires branchés sur l’API auth.
- **DashboardPage** : profil (GET/PUT), photo (upload + blob GET), mot de passe, agrégation **GET `/api/dashboard/stats`**, entretien courant, aperçu des messages post-entretien (`/api/post-interview/{id}/messages`).
- **InterviewPage** : démarrage entretien, réponses multipart (audio/texte), lecture TTS (octets audio ou Web Speech), complétion via `POST /api/interview/{id}/complete`, reconnaissance vocale navigateur en secours si le serveur renvoie `browserSttRequired`.
- **Messages** (`/messages`) : placeholder dans `App.jsx` (pas encore d’écran dédié).

**Port et CORS** : le backend n’autorise que **`http://localhost:3000`**. Vite utilise par défaut le port **5173**. Pour le dev local sans toucher au backend : lancer le front avec le port 3000, par exemple :

`npm run dev -- --port 3000`

(depuis `frontend/`).

---

## 3. Backend — stack et versions

| Élément | Détail |
|--------|--------|
| Framework | Spring Boot **3.5.13** |
| Java | **17** |
| Build | Maven (`pom.xml`) |
| API | REST, port **8080** (`server.port`) |
| Persistance | Spring Data JPA, Hibernate `ddl-auto=update`, **PostgreSQL** |
| Sécurité | Spring Security, sessions **stateless**, **JWT** (jjwt 0.12.5), mot de passe BCrypt |
| IA | **LangChain4j** 1.12.2 + **Mistral AI** (`MistralAiChatModel`, modèle `MISTRAL_SMALL_LATEST`) |
| Fichiers | **MinIO** 8.5.7 (photos de profil, bucket configurable) |
| Config locale | **spring-dotenv** — chargement d’un `.env` (répertoire par défaut `${user.dir}`, souvent le module Maven) |
| Tests | `CareerTransitionPlatformApplicationTests` (smoke test) ; H2 en scope test |

Fichiers sensibles : `.env` est ignoré par `.gitignore` (ne pas committer les secrets).

---

## 4. Modèle de données (entités JPA)

Entités repérées : **User**, **Interview**, **Message**, **Recommendation**, **Formation**, **Candidature**.

- **User** : email unique, hash mot de passe, prénom/nom, chemin image (MinIO), **rôle** (`Role`, ex. USER / ADMIN pour l’endpoint admin).
- **Interview** : transcript JSON, analyse de compétences, horodatages, lien utilisateur ; génération de **recommandations** et formations associées côté service.
- **Message** : fil de discussion par **contexte** (ex. `INTERVIEW_{id}` vs flux post-entretien).
- Les flux **Formation** / **Candidature** s’inscrivent dans la phase post-entretien (choix, simulation d’inscription, entreprises, réponses admin).

---

## 5. API REST (aperçu)

**Public (sans JWT)** — `SecurityConfig` :

- `POST /api/auth/register`, `POST /api/auth/login`

**Authentifié (Bearer JWT)** — tout le reste :

- **Profil** (`/api/profile`) : GET/PUT profil, PUT mot de passe, POST photo (multipart), GET photo binaire.
- **Tableau de bord** (`/api/dashboard`) : `GET /api/dashboard/stats` — statuts entretien / formation, compteurs candidatures, **rang Guild** (APPRENTI → MAITRE) et **guildProgressPercent** (logique dans `DashboardService`).
- **Entretien** (`/api/interview`) : démarrage, réponse audio **ou** texte (multipart), messages liés à l’entretien, entretien courant, complétion.
- **Post-entretien** (`/api`) : `POST /post-interview/start`, `POST /post-interview/choice`, `GET /post-interview/{interviewId}/messages`.
- **Admin** : `POST /api/admin/candidature/respond` (rôle ADMIN — défini dans `PostInterviewController`, traite email / statut candidature).

**CORS** : origine autorisée **`http://localhost:3000`**, credentials activés (aligné avec le frontend Vite si lancé sur ce port).

---

## 6. Services externes (clés via `.env` / `application.properties`)

| Domaine | Usage dans le code |
|--------|---------------------|
| **Mistral** | Chat entretien + post-entretien (JSON structuré pour l’UI guidée). |
| **Groq** | Whisper — transcription audio (STT), prioritaire dans `SpeechService`. |
| **AssemblyAI** | Fallback STT si Groq échoue. |
| **ElevenLabs / Unreal Speech** | TTS côté backend (selon implémentation dans `SpeechService`). |
| **PostgreSQL** | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. |
| **MinIO** | `MINIO_*` — stockage objets (photos). |
| **SMTP + IMAP Gmail** | envoi mail et polling (`MAIL_USERNAME` / `MAIL_PASSWORD`) — parcours candidatures / notifications. |
| **JWT** | `JWT_SECRET` (HMAC, doc : ≥ 32 caractères). |

Si la transcription serveur échoue, l’API peut renvoyer `browserSttRequired=true` pour basculer sur la **Web Speech API** côté navigateur.

---

## 7. Fichiers de configuration importants

- `backend/career-transition-platform/src/main/resources/application.properties` — propriétés Spring (DB, JWT, mail, MinIO, clés API, logging DEBUG sur le package applicatif).
- `backend/career-transition-platform/.env` — **non versionné** ; doit définir les variables attendues par les `${...}` du properties.

---

## 8. État Git (instantané utile)

À l’ouverture de session typique : `frontend/` et nouveaux fichiers racine (HTML, images) souvent **non suivis** ; `CONTEXT.md` et `application.properties` peuvent être modifiés ; backend avec ajouts récents (ex. `DashboardController` / `DashboardService`).

---

## 9. Pistes de travail rapides

1. **Messages** : remplacer le placeholder `/messages` par un écran consommant `getPostInterviewMessages` (et flux post-entretien) comme prévu par le dashboard.
2. **Port dev** : documenter ou automatiser le port 3000 (script npm ou proxy Vite) pour éviter les erreurs CORS avec la config actuelle.
3. **Qualité** : étendre les tests (services, sécurité, contrôleurs) au-delà du test de chargement du contexte Spring.
4. **Déploiement** : variables d’environnement pour prod (CORS, secrets, URL DB/MinIO).

---

*Généré pour refléter le dépôt tel qu’exploré ; mettre à jour lors de changements majeurs (nouveaux endpoints, infra, pages frontend).*
