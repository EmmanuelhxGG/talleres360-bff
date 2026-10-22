package com.talleres360.bff.controller;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/registros")
public class RegistroProxyController {
  private record Limite(long inicio,AtomicInteger intentos){}
  private final ConcurrentHashMap<String,Limite> limites=new ConcurrentHashMap<>();
  private final RestClient cliente;private final String url,clave;
  public RegistroProxyController(RestClient ordersClient,@Value("${app.services.orders-url}") String url,
      @Value("${app.services.internal-key}") String clave){cliente=ordersClient;this.url=url;this.clave=clave;}
  @PostMapping public ResponseEntity<Void> solicitar(HttpServletRequest req,@RequestBody byte[] cuerpo){
    if(cuerpo.length>4096)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    long ahora=System.currentTimeMillis();limites.entrySet().removeIf(e->ahora-e.getValue().inicio()>60000);
    if(limites.size()>=1024)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
    var limite=limites.computeIfAbsent(req.getRemoteAddr(),k->new Limite(ahora,new AtomicInteger()));
    if(limite.intentos().incrementAndGet()>30)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
    return cliente.post().uri(url+"/api/registros").header("X-Internal-Key",clave).contentType(MediaType.APPLICATION_JSON).body(cuerpo)
        .exchange((q,r)->ResponseEntity.status(r.getStatusCode()).<Void>build());
  }
  @GetMapping public ResponseEntity<byte[]> listar(){return cliente.get().uri(url+"/api/registros").header("X-Internal-Key",clave)
      .exchange((q,r)->ResponseEntity.status(r.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
          .cacheControl(CacheControl.noStore()).body(r.getBody().readAllBytes()));}
}
