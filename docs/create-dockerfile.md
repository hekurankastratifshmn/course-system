# Create the Dockerfile for the Ubuntu VM

You build the API image on your Windows machine. You then copy that image to the Ubuntu VM and start it there. The VM does not clone the repository, and it does not compile the project.

The finished `Dockerfile` is already in the project root. This page is how that file is written, how Windows turns it into an image, and how that image is started on the VM. In your own project, create the same kind of file and change the jar name so it matches your `pom.xml`.

Docker Desktop on Windows must be using Linux containers. The image is a Linux image, which is why the same file runs on Ubuntu.

## What the image contains

The file uses two stages. The first stage compiles the project. The second stage is the image that runs on the VM.

```text
pom.xml + src
      |
      v
stage "build"   Maven and JDK 21  -->  the jar
      |
      v
stage "run"     JRE 21 and the jar  -->  the image on the VM
```

Maven and the source code stay in the build stage. The running image has the Java runtime and the jar.

## 1. Create `.dockerignore`

In the project root, next to `pom.xml`, create `.dockerignore`:

```text
target
.idea
.git
storage
*.md
```

Docker sends the project folder to the build on Windows. This file keeps the build output, Git data, local uploads, and Markdown files out of that upload.

## 2. Create the `Dockerfile`

In the same folder, create a file named `Dockerfile`. The name has no extension.

Paste this, then read the explanation under it before you change a line:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/course-system-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Stage 1, the build

| Line | What it does |
| --- | --- |
| `FROM maven:3.9.9-eclipse-temurin-21 AS build` | Starts from an image that already has Maven and JDK 21. `AS build` names this stage so the next stage can copy the jar out of it. |
| `WORKDIR /app` | Every later command in this stage runs in `/app` inside the build container. |
| `COPY pom.xml .` | Copies `pom.xml` into `/app`. |
| `COPY src ./src` | Copies `src` into `/app/src`. |
| `RUN mvn -q -DskipTests package` | Compiles the project and writes the jar to `/app/target`. `-q` keeps the log short. `-DskipTests` skips tests during the image build. Run tests on your own machine before you deploy. |

The Java version in this image is 21, the same as `<java.version>` in `pom.xml`. If you change that property, change this `FROM` line to the same JDK.

### Stage 2, the image that runs

A second `FROM` starts a new image. Files from stage 1 are left behind unless you copy them.

| Line | What it does |
| --- | --- |
| `FROM eclipse-temurin:21-jre` | A smaller image with the Java 21 runtime. It has no Maven and no JDK compiler. |
| `WORKDIR /app` | The jar will live in `/app`. |
| `COPY --from=build ... app.jar` | Copies the jar from the build stage and names it `app.jar`. |
| `EXPOSE 8080` | Documents that the API listens on port 8080. This line does not open the port on the VM. The `ports` entry in `docker-compose.yml` does that. |
| `ENTRYPOINT ["java", "-jar", "app.jar"]` | Starts the API when the container starts. The JSON form runs `java` as the main process, so `docker compose stop` can stop it. |

### The jar name

This part of the `COPY` line must match the jar Maven produces:

```text
course-system-0.0.1-SNAPSHOT.jar
```

That name is `<artifactId>-<version>.jar` from `pom.xml`:

```xml
<artifactId>course-system</artifactId>
<version>0.0.1-SNAPSHOT</version>
```

For your own project, change the `COPY` line to your artifact id and version. The name `app.jar` on the right can stay. The `ENTRYPOINT` runs `app.jar`.

Spring Boot's plugin replaces the jar in `target/` with a runnable jar. `java -jar` needs that runnable jar, which is what `mvn package` produces in this project.

## 3. What the Dockerfile does not do

The `Dockerfile` only builds the API. It does not install MySQL, and it does not read `.env` by itself.

`docker-compose.yml` does the rest, both on Windows and on the VM:

- `image: course-system-api:1.0` is the name of the image you copy to the VM.
- `build: .` tells Compose, on Windows, to build that image from the `Dockerfile`.
- The `mysql` service starts MySQL 8 and runs `db/init` the first time its volume is created. Those SQL files are not inside the API image. They are a folder you copy next to the Compose file.
- The API receives `SPRING_DATASOURCE_URL`. In `.env.example` the host in that URL is `mysql`, which is the Compose service name. On the VM, leave that host as `mysql`. The API container reaches the database over the Compose network.
- `COURSE_STORAGE_DIR` is `/data/storage`. Uploaded files go to the `course_files` volume.
- `"8080:8080"` publishes the API on port 8080 of the machine where Compose is running.

## 4. Prepare the Ubuntu VM

Use a 64-bit Ubuntu VM: 22.04, 24.04, or 26.04. Connect with SSH.

Install Docker Engine and the Compose plugin from Docker's apt repository. These are the commands from [Install Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/).

```bash
sudo apt update
sudo apt install ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

sudo tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

sudo apt update
sudo apt install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

Check the install:

```bash
sudo docker run hello-world
docker compose version
```

`hello-world` prints a short success message and exits.

To run Docker without `sudo`, add your user to the `docker` group, then sign out of SSH and sign in again:

```bash
sudo usermod -aG docker $USER
```

## 5. Build the image on Windows

From the project folder in PowerShell, with Docker Desktop running:

```powershell
Copy-Item .env.example .env
docker compose build
```

`docker compose build` runs the `Dockerfile` and stores the result locally as `course-system-api:1.0`. Check it:

```powershell
docker image ls course-system-api
```

Save that image to one file:

```powershell
docker save -o course-system-api.tar course-system-api:1.0
```

`course-system-api.tar` is the file you copy to the VM. It holds the API image only. It does not hold MySQL, and it does not hold `db/init`.

## 6. Copy the image to the VM and start it

The VM needs four things from your Windows machine:

| File or folder | Why the VM needs it |
| --- | --- |
| `course-system-api.tar` | The API image you built |
| `docker-compose.yml` | Starts the API image and MySQL |
| `.env` | Database password and the API connection settings |
| `db/init` | Schema and admin account, loaded the first time MySQL starts |

Create the folder on the VM, then copy the files from PowerShell. Replace `user` and `vm-ip`.

```powershell
ssh user@vm-ip "mkdir -p ~/course-system/db"
scp course-system-api.tar docker-compose.yml .env user@vm-ip:~/course-system/
scp -r db/init user@vm-ip:~/course-system/db/
```

On the VM:

```bash
cd ~/course-system
docker load -i course-system-api.tar
docker compose up -d
```

`docker load` registers `course-system-api:1.0` on the VM. `docker compose up -d` starts that image. Do not add `--build`. `--build` would look for the `Dockerfile` and the source, which are not on the VM.

The VM downloads `mysql:8.0` the first time. That is the database image, not your project. After it is downloaded, Compose creates the database from `db/init` and starts the API.

`-d` keeps the containers running after you disconnect from SSH. Wait until both are up:

```bash
docker compose ps
docker compose logs api
```

The API log contains `Started CourseSystemApplication` when the application is ready. `docker compose ps` shows `course-system-mysql` as healthy before the API stays up, because Compose waits for that health check.

From your laptop, point Postman at the VM:

```text
http://<vm-ip>:8080
```

Change the collection variable `baseUrl` to that address. Run **Login** with `admin@uni-pr.edu` / `admin`, then the other requests.

Open port **8080** in the firewall of the cloud panel that created the VM. Leave port **3307** closed. MySQL is published on 3307 so a database tool on the same machine can connect. The API does not need that port to be open to the internet. Anyone who can reach 3307 can try the database password from `.env`.

Published container ports can bypass `ufw`. Use the cloud firewall to allow 8080 and to block 3307.

## 7. Deploy a new version

On Windows, after the Java code changes:

```powershell
docker compose build
docker save -o course-system-api.tar course-system-api:1.0
scp course-system-api.tar user@vm-ip:~/course-system/
```

On the VM:

```bash
cd ~/course-system
docker load -i course-system-api.tar
docker compose up -d
```

Compose replaces the API container with the loaded image. The MySQL volume is kept, so the admin account and the rows you created stay. `db/init` does not run again.

`docker compose down -v` deletes that volume and the uploaded files. The next start runs `db/init` on an empty database. Use it only when you mean to wipe the VM database.

## If the build or the start fails

- **`COPY` cannot find the jar.** The file name in the `Dockerfile` does not match `target/` after `mvn package`. Set it to `<artifactId>-<version>.jar` from `pom.xml`.
- **The Windows build cannot download Maven or the base images.** Docker Desktop needs internet for the first `docker compose build`.
- **`docker compose up` on the VM tries to build.** The image was not loaded, or the command included `--build`. Run `docker load -i course-system-api.tar` again, then `docker compose up -d`.
- **The API container exits and the log says it cannot connect to MySQL.** `SPRING_DATASOURCE_URL` in `.env` must use the host `mysql`, and the API service must stay behind `depends_on` with `condition: service_healthy`.
- **Postman cannot connect.** `docker compose ps` must show the API container running, and port 8080 must be allowed to your laptop.
