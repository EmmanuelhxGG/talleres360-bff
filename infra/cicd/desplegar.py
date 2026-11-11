#!/usr/bin/env python3
"""Instala una entrega validada de GitHub sobre el servicio Compose existente."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import urlopen

ESTADO = Path("/home/ec2-user/cicd-talleres360")
CONFIG = Path("/etc/talleres360-cicd.json")
IMAGEN = "eclipse-temurin:21-jre-alpine"


def ejecutar(*args):
    subprocess.run(args, check=True, timeout=180)


def escribir(ruta, texto):
    temporal = ruta.with_suffix(ruta.suffix + ".tmp")
    temporal.write_text(texto, encoding="utf-8")
    os.replace(temporal, ruta)


def actualizar():
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    url = f"https://github.com/{config['repo']}/releases/download/ci-ec2"
    activa = ESTADO / "compose.cicd.yml"
    anterior = ESTADO / "compose.anterior.yml"
    en_curso = ESTADO / "en-curso"
    instalada = ESTADO / "revision"
    fallida = ESTADO / "revision-fallida"
    base = ["docker", "compose", "--project-directory", config["directorio"],
            "--env-file", config["env"], "--project-name", config["proyecto"]]
    for archivo in config["compose"]:
        base.extend(["-f", archivo])

    def levantar():
        comando = base + (["-f", str(activa)] if activa.exists() else [])
        ejecutar(*comando, "up", "-d", "--no-deps", "--no-build", "--pull", "never",
                 config["servicio"])

    def comprobar():
        url_salud = f"http://127.0.0.1:{config['puerto']}{config['salud']}"
        for _ in range(45):
            try:
                with urlopen(url_salud, timeout=2) as respuesta:
                    if respuesta.status == config["estado_http"]:
                        return
            except HTTPError as error:
                if error.code == config["estado_http"]:
                    return
            except (URLError, TimeoutError, ConnectionError):
                pass
            time.sleep(2)
        raise RuntimeError("El servicio no respondió a la comprobación HTTP")

    def restaurar():
        if anterior.exists():
            escribir(activa, anterior.read_text(encoding="utf-8"))
        else:
            activa.unlink(missing_ok=True)
        levantar()
        comprobar()
        en_curso.unlink()

    if en_curso.exists():
        if instalada.exists() and instalada.read_bytes() == en_curso.read_bytes():
            en_curso.unlink()
        else:
            restaurar()

    try:
        with urlopen(f"{url}/version.txt", timeout=12) as respuesta:
            manifiesto = respuesta.read(256).decode("ascii").strip()
    except HTTPError as error:
        if error.code == 404:
            return
        raise
    if not re.fullmatch(r"[0-9a-f]{40} [0-9a-f]{64}", manifiesto):
        raise ValueError("El manifiesto de entrega no tiene el formato esperado")
    revision, huella = manifiesto.split()
    for marca in (instalada, fallida):
        if marca.exists() and marca.read_text(encoding="utf-8").strip() == revision:
            return

    with tempfile.TemporaryDirectory(dir=ESTADO) as tmp:
        descarga = Path(tmp) / "app.jar"
        ejecutar("curl", "-fsSL", "--max-time", "90", "--max-filesize", "200000000",
                 "-o", str(descarga), f"{url}/app.jar")
        with descarga.open("rb") as archivo:
            calculada = huella_sha256(archivo)
        if calculada != huella:
            raise RuntimeError("El JAR no coincide con el SHA-256 del manifiesto")

        if subprocess.run(["docker", "image", "inspect", IMAGEN],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            ejecutar("docker", "pull", IMAGEN)
        destino = ESTADO / f"{revision}.jar"
        descarga.chmod(0o644)
        os.replace(descarga, destino)

    anterior.unlink(missing_ok=True)
    if activa.exists():
        shutil.copyfile(activa, anterior)
    escribir(en_curso, revision)
    servicio = {
        "image": IMAGEN,
        "entrypoint": ["java", "-XX:MaxRAMPercentage=70.0", "-jar", "/app-ci/app.jar"],
        "command": [],
        "volumes": [f"{destino}:/app-ci/app.jar:ro"],
    }
    escribir(activa, json.dumps({"services": {config["servicio"]: servicio}}))
    try:
        levantar()
        comprobar()
    except Exception:
        escribir(fallida, revision)
        restaurar()
        raise
    escribir(instalada, revision)
    en_curso.unlink()
    fallida.unlink(missing_ok=True)
    conservar = activa.read_text(encoding="utf-8")
    if anterior.exists():
        conservar += anterior.read_text(encoding="utf-8")
    for jar in ESTADO.glob("*.jar"):
        if re.fullmatch(r"[0-9a-f]{40}\.jar", jar.name) and str(jar) not in conservar:
            jar.unlink()
    print(f"Despliegue verificado: {config['repo']} {revision}", flush=True)


def huella_sha256(archivo):
    huella = hashlib.sha256()
    for bloque in iter(lambda: archivo.read(1024 * 1024), b""):
        huella.update(bloque)
    return huella.hexdigest()


if __name__ == "__main__":
    with (ESTADO / "bloqueo").open("a") as bloqueo:
        fcntl.flock(bloqueo, fcntl.LOCK_EX | fcntl.LOCK_NB)
        actualizar()
