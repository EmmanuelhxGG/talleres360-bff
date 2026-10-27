package com.talleres360.bff.controller;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/registros")
public class RegistroProxyController {
  private static final java.util.regex.Pattern CORREO_DUOC = java.util.regex.Pattern.compile(
      "(?i)^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@duocuc[.]cl$");

  private static String correoRegistro(String valor) {
    if (valor == null) return null;
    String correo = valor.strip().toLowerCase(java.util.Locale.ROOT);
    if (correo.length() > 150 || !CORREO_DUOC.matcher(correo).matches()) return null;
    String local = correo.substring(0, correo.indexOf('@'));
    return local.length() <= 64 && !local.startsWith(".") && !local.endsWith(".") && !local.contains("..") ? correo : null;
  }
  private record Limite(long inicio,AtomicInteger intentos){}
  private final ConcurrentHashMap<String,Limite> limites=new ConcurrentHashMap<>();
  private final RestClient cliente;private final String url,clave;
  private final ObjectMapper json;
  public RegistroProxyController(RestClient ordersClient,@Value("${app.services.orders-url}") String url,
      @Value("${app.services.internal-key}") String clave,ObjectMapper json){cliente=ordersClient;this.url=url;this.clave=clave;this.json=json;}
  @PostMapping public ResponseEntity<Void> solicitar(HttpServletRequest req,JwtAuthenticationToken usuario,@RequestBody byte[] cuerpo){
    if(cuerpo.length>4096)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    String correo = java.util.stream.Stream.of("email", "preferred_username", "upn")
        .map(usuario.getToken()::getClaimAsString).map(RegistroProxyController::correoRegistro)
        .filter(java.util.Objects::nonNull).findFirst().orElseThrow(() ->
        new ResponseStatusException(HttpStatus.FORBIDDEN,
            "El registro requiere una cuenta @duocuc.cl autenticada"));
    String nombre;
    try {
      var entrada = json.readTree(cuerpo);
      if (entrada == null || !entrada.isObject() || !entrada.path("nombre").isTextual())
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solicitud inválida");
      nombre = java.text.Normalizer.normalize(entrada.path("nombre").asText(), java.text.Normalizer.Form.NFC).strip();
      if (!nombre.matches("^[\\p{L} ]{3,120}$"))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre inválido");
      if (entrada.has("correo") && (!entrada.path("correo").isTextual()
          || !correo.equals(correoRegistro(entrada.path("correo").asText()))))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo debe coincidir con la cuenta autenticada");
    }
    catch(java.io.IOException error){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Solicitud inválida");}
    long ahora=System.currentTimeMillis();limites.entrySet().removeIf(e->ahora-e.getValue().inicio()>60000);
    if(limites.size()>=1024)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
    var limite=limites.computeIfAbsent(usuario.getToken().getSubject(),k->new Limite(ahora,new AtomicInteger()));
    if(limite.intentos().incrementAndGet()>30)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
    return cliente.post().uri(url+"/api/registros").header("X-Internal-Key",clave).contentType(MediaType.APPLICATION_JSON).body(java.util.Map.of("nombre",nombre,"correo",correo))
        .exchange((q,r)->ResponseEntity.status(r.getStatusCode()).<Void>build());
  }
  @GetMapping public ResponseEntity<byte[]> listar(){return cliente.get().uri(url+"/api/registros").header("X-Internal-Key",clave)
      .exchange((q,r)->ResponseEntity.status(r.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
          .cacheControl(CacheControl.noStore()).body(r.getBody().readAllBytes()));}
}
