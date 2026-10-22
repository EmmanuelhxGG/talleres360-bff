package com.talleres360.bff.controller;
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
    String correo=null;
    for(String claim:new String[]{"email","preferred_username","upn"}){
      String valor=usuario.getToken().getClaimAsString(claim);
      if(valor!=null && valor.matches("(?i)^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@duocuc\\.cl$")){correo=valor.toLowerCase(java.util.Locale.ROOT);break;}
    }
    if(correo==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Se requiere correo @duocuc.cl");
    if(cuerpo!=null&&cuerpo.length>32768)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    var llamada=cliente.method(HttpMethod.valueOf(request.getMethod())).uri(URI.create(ordenes+"/api/compras"))
        .header("X-Customer-Email",correo).header("X-Internal-Key",clave);
    if(cuerpo!=null)llamada.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
    return llamada.exchange((req,res)->ResponseEntity.status(res.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
        .cacheControl(CacheControl.noStore()).body(res.getBody().readAllBytes()));
  }
}
