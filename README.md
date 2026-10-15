# Secure Asynchronous Employee REST API

A Spring Boot API where an authenticated client:

1. Gets an access token using a client ID and secret.
2. Starts a transaction.
3. Submits employee data. The API replies `202 Accepted` at once and saves the record in the background.
4. Checks the transaction status and reads the saved employee record from PostgreSQL.

**Tech:** Java 21, Spring Boot 3.5, Spring Security (JWT), Spring Data JPA, PostgreSQL, Flyway, JUnit 5, Mockito.

## Requirements

- Java 21
- Maven 3.9+
- PostgreSQL 14+ (verified on 17.11)

## 1. Database setup

Run once as the PostgreSQL admin user:

```bash
psql -h localhost -U postgres -v app_password='choose-a-password' -f db/setup.sql
```

This creates the `app_user` role (not a superuser), the `app_db` database, and two schemas: `app_schema_dev` (app) and `app_schema_test` (tests). Flyway creates the tables when the app starts.

## 2. Configuration

```bash
cp .env.example .env     # .env is git-ignored; fill in the values
```

| Variable | Description |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_SCHEMA` | Default to `localhost`, `5432`, `app_db`, `app_schema_dev` |
| `DB_USERNAME`, `DB_PASSWORD` | Database login (required) |
| `JWT_SECRET` | Token signing key of at least 32 characters, e.g. `openssl rand -base64 48` |
| `ASSESSMENT_CLIENT_SECRET_HASH` | BCrypt hash of the `assessment_client` secret (required) |
| `SECONDARY_CLIENT_SECRET_HASH` | BCrypt hash of the `secondary_client` secret (optional) |
| `TEST_DB_SCHEMA` | Schema for integration tests (default `app_schema_test`) |

Client secrets are stored only as BCrypt hashes. To create one:

```bash
htpasswd -bnBC 12 "" 'my-client-secret' | tr -d ':\n'; echo
```

Put the hash in single quotes in `.env`, because it contains `$` characters.

A hash cannot be turned back into the secret, so keep the plaintext secrets for local testing in `.env.secrets`. That file is git-ignored and the app does not read it.

```bash
A=$(openssl rand -hex 16); S=$(openssl rand -hex 16)
(umask 077; printf 'assessment_client=%s\nsecondary_client=%s\n' "$A" "$S" > .env.secrets)
htpasswd -bnBC 12 "" "$A" | tr -d ':\n'; echo   # -> ASSESSMENT_CLIENT_SECRET_HASH
htpasswd -bnBC 12 "" "$S" | tr -d ':\n'; echo   # -> SECONDARY_CLIENT_SECRET_HASH
unset A S
```

The app reads the hashes only at startup. After changing them in `.env`, stop the app, run `set -a; source .env; set +a` again, and restart it. Otherwise token requests fail.

### Add another test client

The app already has `assessment_client` and `secondary_client`. To add a third one:

1. Generate a plaintext secret and its hash:

```bash
SECRET=$(openssl rand -hex 16)
echo "$SECRET"
htpasswd -bnBC 12 "" "$SECRET" | tr -d ':\n'; echo
```

2. Append `your_client_id=<plaintext>` to `.env.secrets`. Put the hash in `.env` as a new variable, for example `THIRD_CLIENT_SECRET_HASH='...'` (single quotes).

3. Add a client entry under `app.security.clients` in `src/main/resources/application.yml`:

```yaml
      - client-id: ${THIRD_CLIENT_ID:your_client_id}
        secret-hash: ${THIRD_CLIENT_SECRET_HASH}
        scopes: [transactions:write, transactions:read]
```

4. Restart the app with `.env` sourced. Token request:

```json
{ "client_id": "your_client_id", "client_secret": "<plaintext>" }
```

Do not send `clientId` / `clientSecret`. The API and Swagger both use snake_case.

## 3. Run and test

```bash
set -a; source .env; set +a   # load the environment variables

mvn test                      # unit tests (no database needed)
mvn verify                    # unit and integration tests (uses PostgreSQL)
mvn spring-boot:run           # start the API on http://localhost:8080
```

Interactive docs: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (OpenAPI at `/v3/api-docs`). Call **POST /api/v1/auth/token** first with snake_case fields (`client_id`, `client_secret`), copy `access_token`, click **Authorize**, paste the token with no `Bearer ` prefix, then run init, submit, status, and read in that order.

### Run a packaged jar

Build the jar (the runnable file is the one without `-sources` or `-javadoc` in the name):

```bash
mvn -DskipTests package
# target/secure-async-employee-api-1.0.0.jar
```

### Running the jar on another machine

If someone receives only the jar, they need Java 21, PostgreSQL 14+, `htpasswd` (package `apache2-utils`) and `curl`. Share `db/setup.sql` and `.env.example` with the jar, but not your own `.env`. They should create their own secrets.

**1. Create the database.** The password chosen here becomes `DB_PASSWORD`:

```bash
psql -h localhost -U postgres -v app_password='choose-a-db-password' -f setup.sql
```

**2. Create client secrets and their hashes** as described in section 2: plaintext goes in `.env.secrets` and hashes go in `.env`. Also generate a value for `JWT_SECRET`:

```bash
openssl rand -base64 48
```

**3. Create `.env`** from `.env.example`. Fill in `DB_PASSWORD`, `JWT_SECRET` and `ASSESSMENT_CLIENT_SECRET_HASH`, and put the hash in single quotes. You can leave `SECONDARY_CLIENT_SECRET_HASH` empty.

**4. Start the API.** The jar does not read `.env` by itself, so export the variables in the same shell first. Otherwise startup fails with `password authentication failed for user "${DB_USERNAME}"`.

```bash
set -a; source .env; set +a
java -jar secure-async-employee-api-1.0.0.jar
```

The API listens on http://localhost:8080. Flyway creates the tables on startup. The database, role and schema must already exist from step 1.

**5. Test with curl** in a second terminal. The plaintext secret is read from `.env.secrets`, not the hash from `.env`. In zsh, paste the commands without `#` comment lines, or run `setopt interactivecomments` first.

```bash
BASE=http://localhost:8080/api/v1
SECRET=$(grep '^assessment_client=' .env.secrets | cut -d= -f2-)
TOKEN=$(curl -s -X POST $BASE/auth/token -H 'Content-Type: application/json' \
  -d "{\"client_id\":\"assessment_client\",\"client_secret\":\"$SECRET\"}" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("access_token",""))')
unset SECRET
[ -n "$TOKEN" ] && echo "token ok" || echo "token failed: check the secret and hash"

TXN=$(curl -s -X POST $BASE/transactions/init -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("transaction_id",""))')
echo $TXN

curl -s -w '\nHTTP %{http_code}\n' -X POST $BASE/transactions/$TXN/employees \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Test User","dob":"1995-08-20","gender":"FEMALE","pan":"TESTA0001Z","mobile_number":"8123456789"}'

curl -s $BASE/transactions/$TXN -H "Authorization: Bearer $TOKEN"; echo
curl -s $BASE/transactions/$TXN/employees -H "Authorization: Bearer $TOKEN"; echo
```

Expected results: the submit returns `HTTP 202`, the status call returns `COMPLETED`, and the last call returns the saved record. Use a new PAN for each run, because a repeated PAN ends as `FAILED` with `DUPLICATE_PAN`.

If the token fails, everything after it also fails. Init returns `401`, `TXN` stays empty, and the URL becomes `/transactions//employees`, which returns `400`. Fix the token first. The usual causes are:
- `.env.secrets` is missing or was regenerated without updating the hashes in `.env`.
- The hashes in `.env` were changed but the app was not restarted. It reads them only at startup.

## Endpoints

Every endpoint except the token endpoint needs the header `Authorization: Bearer <token>`.

| Method | Path | Result |
|---|---|---|
| POST | `/api/v1/auth/token` | `200` with the token |
| POST | `/api/v1/transactions/init` | `201` with a new transaction ID |
| POST | `/api/v1/transactions/{id}/employees` | `202`; the record is saved in the background |
| GET | `/api/v1/transactions/{id}` | `200` with the status |
| GET | `/api/v1/transactions/{id}/employees` | `200` with the saved record |

**Token request:**
```json
{ "client_id": "assessment_client", "client_secret": "your-secret" }
```
Response: `{ "access_token": "...", "token_type": "Bearer", "expires_in": 1800 }`

**Employee request:**
```json
{ "name": "Amit Kumar", "dob": "1990-05-15", "gender": "MALE", "pan": "ABCDE1234F", "mobile_number": "9876543210" }
```

**Status responses:**
```json
{ "transaction_id": "TXN-8F21A9C4D2E7B610", "status": "COMPLETED", "message": "Employee data saved successfully" }
{ "transaction_id": "TXN-8F21A9C4D2E7B610", "status": "FAILED", "error": { "code": "DUPLICATE_PAN", "message": "Employee PAN already exists" } }
```

A transaction moves through these statuses: `INITIALIZED` -> `ACCEPTED` -> `PROCESSING` -> `COMPLETED` or `FAILED`.

## Validation rules

| Field | Rule |
|---|---|
| `name` | 2-100 characters: letters, spaces, `.`, `'`, `-` (leading and trailing spaces are trimmed) |
| `dob` | `YYYY-MM-DD`, a real date in the past |
| `gender` | `MALE`, `FEMALE` or `OTHER` (any letter case) |
| `pan` | 5 uppercase letters, 4 digits, 1 uppercase letter, e.g. `ABCDE1234F`; must be unique |
| `mobile_number` | A string of 10 digits starting with 6, 7, 8 or 9 |

## HTTP status codes

| Code | When |
|---|---|
| 200 / 201 / 202 | Success |
| 400 | Invalid JSON or invalid field; `details` explains each failing field |
| 401 | Missing, invalid or expired token, or wrong client credentials |
| 403 | The token lacks the required permission (scope) |
| 404 | The transaction doesn't exist or belongs to another client |
| 409 | Duplicate PAN, data already submitted for this transaction, or record not ready yet |
| 500 | Unexpected error (generic message only) |

Error format:
```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "code": "VALIDATION_FAILED",
  "message": "Employee validation failed", "details": { "pan": "PAN must be 5 uppercase letters, 4 digits and 1 uppercase letter" } }
```

## Quick demo with curl

```bash
BASE=http://localhost:8080/api/v1

TOKEN=$(curl -s -X POST $BASE/auth/token -H 'Content-Type: application/json' \
  -d '{"client_id":"assessment_client","client_secret":"<secret>"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')

TXN=$(curl -s -X POST $BASE/transactions/init -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["transaction_id"])')

curl -s -X POST $BASE/transactions/$TXN/employees -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Amit Kumar","dob":"1990-05-15","gender":"MALE","pan":"ABCDE1234F","mobile_number":"9876543210"}'

curl -s $BASE/transactions/$TXN -H "Authorization: Bearer $TOKEN"
curl -s $BASE/transactions/$TXN/employees -H "Authorization: Bearer $TOKEN"
```

To see the error cases:

- Submit to the same transaction again: `409`.
- Submit the same PAN in a new transaction: `409 DUPLICATE_PAN`.
- Read the transaction with a `secondary_client` token: `404`.
- Call any endpoint without a token: `401`.

A Postman collection is in `postman/secure-async-employee-api.postman_collection.json`.

## CI and release

GitHub Actions workflows live in `.github/workflows/`.

- **CI** (`ci.yml`) runs on pull requests and on pushes to `main`/`master`. It builds with Java 21 and runs `mvn verify` (unit tests and integration tests) against PostgreSQL 17. Integration tests use the `app_schema_test` schema created by the workflow; they do not read `.env` or `.env.secrets`.
- **Release** (`release.yml`) runs when you publish a GitHub Release. It rebuilds, re-runs `mvn verify`, and attaches the runnable jar (`target/secure-async-employee-api-<version>.jar`), `db/setup.sql`, and `.env.example` to that release.

To publish a package: create a git tag, then **Releases → Draft a new release** for that tag and publish it. After the workflow finishes, download the jar from the release assets. Recipients still need Java 21 and PostgreSQL; follow **Running the jar on another machine** above.

## Project structure

```
src/main/java/com/example/employeeapi/
  auth/         token endpoint, client check, JWT creation
  config/       security, JWT, async executor, JSON settings
  transaction/  transaction entity, status changes, controller
  employee/     validation, submission, background worker, saving
  error/        shared error responses
src/main/resources/db/migration/   Flyway SQL (tables and constraints)
src/test/java/                     unit tests (*Test) and integration tests (*IT)
db/setup.sql                       database, role and schema setup
```

## Notes and limitations

- Background processing runs in an in-memory thread pool, not a durable queue. If the app crashes after replying `202`, that submission is lost and the transaction stays `ACCEPTED` or `PROCESSING`.
- A `FAILED` or `COMPLETED` transaction can't be reused; start a new one.
- Tokens expire after 30 minutes and can't be revoked earlier.
- PAN uniqueness is enforced by a database constraint. If two requests race past the duplicate check, the losing one ends as `FAILED` with `DUPLICATE_PAN`.
- Logs never contain tokens, secrets, full PANs or mobile numbers.
