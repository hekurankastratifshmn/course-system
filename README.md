# University Course System

A small REST API for a university course catalogue. It keeps users, courses, enrollments, and course files. The API is a Spring Boot application. MySQL stores the rows. Uploaded files stay on disk, and the database stores only the file name.

Docker Compose starts the API and MySQL together. That is the setup to use for the course.

## What you need

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (includes Docker Compose)
- Git
- [Postman](https://www.postman.com/downloads/), or any other HTTP client

You do not need Java installed to run the project. The API container builds the application for you.

## Start the application

From the project folder:

**Windows (PowerShell)**

```powershell
Copy-Item .env.example .env
docker compose up --build
```

**macOS or Linux**

```bash
cp .env.example .env
docker compose up --build
```

The first start downloads the MySQL image and builds the API. Wait until the `api` log shows that the application has started. Leave this terminal open. The containers stop when you press `Ctrl+C`.

| Service | Address | Purpose |
| --- | --- | --- |
| API | http://localhost:8080 | The REST API |
| MySQL | `localhost:3307` | The database. The port on your machine is **3307**, because 3306 is often already taken. |

`.env` holds the database password used by Compose. It is not committed to Git. `.env.example` has the values for this class. Keep those values unless you also change the database that is already running.

`db/init` is loaded only the first time the MySQL volume is created. Later starts reuse the same data.

## Sign in

Every request except login needs a Bearer token. Login returns a token that lasts 8 hours. Send it on the next requests:

```http
Authorization: Bearer <token>
```

One account is already in the database:

| Name | Email | Password | Role |
| --- | --- | --- | --- |
| System Administrator | admin@uni-pr.edu | admin | ADMIN |

Professors, students, and courses are empty until you create them. The Postman collection does that after login.

The password is checked against a hash. The `users` table does not store the password itself.

A valid token is enough. The API does not look at the role on every request. The role matters when a user is saved as the professor of a course, or when a user is enrolled: that user must be a `PROFESSOR` or a `STUDENT`.

## Call the API with curl

Login, then call a protected endpoint with the token.

**PowerShell**

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login -ContentType "application/json" -Body '{"email":"admin@uni-pr.edu","password":"admin"}'
$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod -Uri http://localhost:8080/hello -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8080/courses?semester=2026-W&status=OPEN" -Headers $headers
```

**bash**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@uni-pr.edu","password":"admin"}' | python -c "import sys, json; print(json.load(sys.stdin)['token'])")

curl http://localhost:8080/hello -H "Authorization: Bearer $TOKEN"
curl "http://localhost:8080/courses?semester=2026-W&status=OPEN" -H "Authorization: Bearer $TOKEN"
```

`/hello` returns `We are learning the course Cloud Computing!`. The course list is empty until you create a course.

## Call the API with Postman

1. Open Postman.
2. Choose **Import** and select `postman/University-Course-System.postman_collection.json`.
3. Run **Login** first. The collection saves the token and sends it on the other requests.
4. Each other item is one endpoint. The body is an example. Change it and send the same request again for another person or course.

Ids are saved in collection variables:

| Request | Variable | Used by |
| --- | --- | --- |
| Create user | `userId` | Get user |
| Create user, when the role is `PROFESSOR` | `professorId` | Create course |
| Create user, when the role is `STUDENT` | `studentId` | Enroll student, List student courses |
| Create course | `courseId` | The other course, enrollment, and material requests |
| Enroll student | `enrollmentId` | Cancel enrollment |
| Upload material | `materialId` | Download material, Delete material |

Create the professor before **Create course** if the body includes `professorId`. Create the student before **Enroll student**. To enroll a second student, change the **Create user** body, send it, then send **Enroll student** again. The same email or course code returns 409.

**Upload material** needs a file chosen in the form body.

The collection variable `baseUrl` is `http://localhost:8080`.

## Endpoints

Errors come back as JSON: `{"error":"..."}`.

| Method | Path | What it does | Success |
| --- | --- | --- | --- |
| POST | `/auth/login` | Check email and password. Body: `{"email","password"}`. | 200, with `token` |
| GET | `/hello` | A short text response. | 200 |
| GET | `/health` | `UP` when the API can query MySQL. | 200, or 503 |
| GET | `/courses?semester=&status=` | List courses. `status` is `OPEN` or `CLOSED`. Both filters are optional. | 200 |
| GET | `/courses/{id}` | One course, including its professor. | 200 |
| POST | `/courses` | Create a course. It starts as `OPEN`. Body: `code`, `name`, `credits`, `semester`, optional `professorId`. | 201 |
| PUT | `/courses/{id}` | Change any of `code`, `name`, `credits`, `semester`, `status`, `professorId`. Send only the fields you want to change. | 200 |
| DELETE | `/courses/{id}` | Remove a course that has no enrollments and no files. | 204 |
| POST | `/courses/{id}/enrollments` | Enroll a student. Body: `{"studentId": <id>}`. The course must be `OPEN`. | 201 |
| GET | `/courses/{id}/enrollments` | Students still enrolled in the course. | 200 |
| GET | `/students/{id}/courses` | Courses that student is enrolled in. | 200 |
| DELETE | `/enrollments/{id}` | Set that enrollment to `CANCELLED`. | 200 |
| POST | `/users` | Create a user. Body: `name`, `email`, `role`, `password`. Role is `STUDENT`, `PROFESSOR`, or `ADMIN`. | 201 |
| GET | `/users/{id}` | One user. The password hash is not returned. | 200 |
| POST | `/courses/{id}/materials` | Upload a file. Form fields: `title` and `file`. | 201 |
| GET | `/courses/{id}/materials` | Files recorded for that course. | 200 |
| GET | `/materials/{id}` | Download the file. | 200 |
| DELETE | `/materials/{id}` | Delete the row and the file. | 204 |

Useful status codes:

| Code | When you see it |
| --- | --- |
| 400 | A required field is missing, the role is unknown, or the status is not `OPEN` or `CLOSED`. |
| 401 | The token is missing, expired, or wrong. Login again. |
| 404 | That user, course, enrollment, or file does not exist. |
| 409 | Duplicate email or course code, enrolling the same student twice, enrolling in a closed course, or deleting a course that still has students or files. |

Uploads larger than 20 MB are rejected.

## Where each part lives

```text
db/init/01-schema.sql     tables
db/init/02-seed.sql       the admin account
postman/                  the Postman collection
src/main/java/.../api     the HTTP endpoints
docker-compose.yml        API + MySQL
Dockerfile                how the API image is built
.env.example              database settings to copy
```

MySQL tables:

- `users` — people who can log in
- `courses` — the catalogue
- `enrollments` — which student joined which course
- `course_materials` — title and file name for an upload
- `tokens` — the Bearer token from login

Inside Docker, uploaded files go to the `course_files` volume at `/data/storage`. The file name in the database looks like `<courseId>-<random>-syllabus.pdf`.

## Stop, start, and reset

Start again in the background after the image is already built:

```bash
docker compose up -d
```

Stop the containers and keep the data:

```bash
docker compose stop
```

Stop and remove the containers, and keep the database volume:

```bash
docker compose down
```

Remove the containers, the database, and the uploaded files. The next `docker compose up` loads `db/init` again. Use this after you change a file in `db/init`, because those files do not run again on an existing volume.

```bash
docker compose down -v
```

## Run the API from your editor

Use this when you want to change Java code and restart quickly. You still need Docker for MySQL, and you need JDK 21.

Start only the database:

```bash
docker compose up mysql
```

Then, in another terminal, from the project folder:

**PowerShell**

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3307/course_system?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC&connectionTimeZone=UTC"
$env:SPRING_DATASOURCE_USERNAME = "localdb"
$env:SPRING_DATASOURCE_PASSWORD = "localdb"
.\mvnw.cmd spring-boot:run
```

**bash**

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3307/course_system?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC&connectionTimeZone=UTC"
export SPRING_DATASOURCE_USERNAME="localdb"
export SPRING_DATASOURCE_PASSWORD="localdb"
./mvnw spring-boot:run
```

Files uploaded this way are saved in the `storage` folder of the project.

## If something fails

- **Compose says a variable is missing.** Copy `.env.example` to `.env` and run the command from the project folder.
- **Port 8080 or 3307 is already in use.** Stop the other program, or change the left-hand port in `docker-compose.yml` (`"8080:8080"` or `"3307:3306"`). If you change 8080, use the new port in Postman too.
- **Login returns 401, or the API never becomes ready.** Read the `mysql` logs. If `02-seed.sql` failed on an earlier attempt, reset with `docker compose down -v` and start again.
- **You edited `db/init` and the old data is still there.** Init scripts run once. Reset with `docker compose down -v`.
- **Every request returns 401.** Run login again and send `Authorization: Bearer <token>`.
- **Creating a course with `professorId` returns 400.** That user must already exist and have the role `PROFESSOR`.
- **You changed the password in `.env` and MySQL rejects it.** The password is stored in the volume from the first start. Reset with `docker compose down -v`, or put the original password back.
