package com.talleres360.bff.controller;
import com.talleres360.bff.service.CorreoAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ComprasProxyController {
  private final RestClient cliente;
  private final String ordenes,clave;
  public ComprasProxyController(RestClient ordersClient,@Value("${app.services.orders-url}") String url,
      @Value("${app.services.internal-key}") String key){cliente=ordersClient;ordenes=url;clave=key;}
  @RequestMapping(path="/api/compras",method={RequestMethod.GET,RequestMethod.POST})
  public ResponseEntity<byte[]> enviar(HttpServletRequest request,JwtAuthenticationToken usuario,
      @RequestBody(required=false) byte[] cuerpo){
    String correo = CorreoAutenticado.desde(usuario.getToken()).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Se requiere una cuenta @duocuc.cl o de un dominio onmicrosoft.com autorizada"));
    if(cuerpo!=null&&cuerpo.length>32768)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    String nombre = usuario.getToken().getClaimAsString("name");
    if (nombre == null || nombre.isBlank()) nombre = correo;
    if (nombre.length() > 120 || nombre.codePoints().anyMatch(Character::isISOControl))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El nombre de la cuenta no es válido");
    var llamada=cliente.method(HttpMethod.valueOf(request.getMethod())).uri(URI.create(ordenes+"/api/compras"))
        .header("X-Customer-Email",correo)
        .header("X-Customer-Name-Encoded",java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(nombre.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .header("X-Internal-Key",clave);
    if(cuerpo!=null)llamada.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
    return llamada.exchange((req,res)->ResponseEntity.status(res.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
        .cacheControl(CacheControl.noStore()).body(res.getBody().readAllBytes()));
  }
}
