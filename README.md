# ArcLogin (Paper 1.21.8 · servidor offline independiente)

## Compilar -> ArcLogin.jar
Opcion A (local): instala JDK 21 + Maven y ejecuta `build.bat` (Windows) o `./build.sh`.
              El jar queda en `target/ArcLogin.jar`.
Opcion B (sin instalar nada): sube esta carpeta a un repo de GitHub; en la pestaña
              Actions corre "Build ArcLogin" y descarga el artefacto `ArcLogin-jar`.

Copia el jar a `plugins/` y reinicia.

## Comandos
/register <pass> <pass> · /login <pass> · /changepassword <actual> <nueva> · /premium
/setloginspawn      -> donde esperan los jugadores antes de loguearse
/setregisterspawn   -> donde aparecen la primera vez que se registran
/arclogin reload | unregister <j> | setpremium <j> <true|false>
          | setloginspawn | setregisterspawn | delspawn <login|register> | tpspawn <login|register>
Permiso admin: arclogin.admin (op por defecto)

## Nota sobre /premium en servidor offline
Sin online-mode no se puede probar que alguien es dueño de la cuenta original.
/premium solo comprueba con Mojang que el nombre exista como cuenta premium y lo marca;
por seguridad todos siguen usando contraseña.
