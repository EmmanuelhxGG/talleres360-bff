package com.talleres360.bff.controller;
import com.talleres360.bff.service.CorreoAutenticado;
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
  private record Limite(long inicio,AtomicInteger intentos){}
  private final ConcurrentHashMap<String,Limite> limites=new ConcurrentHashMap<>();
  private final RestClient cliente;private final String url,clave;
  private final ObjectMapper json;
  public RegistroProxyController(RestClient ordersClient,@Value("${app.services.orders-url}") String url,
      @Value("${app.services.internal-key}") String clave,ObjectMapper json){cliente=ordersClient;this.url=url;this.clave=clave;this.json=json;}
  @PostMapping public ResponseEntity<Void> solicitar(HttpServletRequest req,JwtAuthenticationToken usuario,@RequestBody byte[] cuerpo){
    if(cuerpo.length>4096)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    String correo = CorreoAutenticado.desde(usuario.getToken()).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Se requiere una cuenta @duocuc.cl o de un dominio onmicrosoft.com autorizada"));
    String nombre;
    try { nombre=json.readTree(cuerpo).path("nombre").asText(""); }
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
