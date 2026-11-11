# CI/CD de Talleres360

Cada push en la rama del servicio ejecuta Maven verify con Java 21. Las pull requests ejecutan CI. Una entrega validada actualiza el JAR y su manifiesto SHA-256 en la prerelease ci-ec2 del mismo repositorio.

Cada EC2 ejecuta talleres360-cicd.timer y consulta GitHub cada minuto. Si hay una revisión nueva, valida la huella, recrea solo el servicio de aplicación de su Compose y verifica su respuesta HTTP. Si falla la comprobación, repone la versión anterior y espera a una revisión distinta. En el siguiente inicio de la EC2, el temporizador consulta la última versión disponible.

Las rutas de Compose, los .env y los volúmenes se toman del despliegue local de cada instancia. El JAR se monta en modo de solo lectura sobre Java 21. Los cambios de infraestructura o entorno, incluidos el broker Kafka, la activación de Kafka y las migraciones incompatibles con la versión anterior, se coordinan por separado.

## Verificar una entrega

- En GitHub abre Actions, entra a CI y entrega a EC2 y revisa verificar y publicar.
- En cada EC2 consulta:

    systemctl list-timers talleres360-cicd.timer
    journalctl -u talleres360-cicd.service -n 30 --no-pager
    cat /home/ec2-user/cicd-talleres360/revision

El commit desplegado en la EC2 debe coincidir con la primera columna de version.txt en la prerelease ci-ec2. Un estado HTTP de autenticación en un endpoint protegido confirma que el servidor responde. La comprobación de extremo a extremo de las integraciones es una prueba adicional.

Para pausar las entregas en una EC2:

    sudo systemctl disable --now talleres360-cicd.timer

Para reanudarlas:

    sudo systemctl enable --now talleres360-cicd.timer
