package no.fk.fint.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import no.fk.Ansatt;
import no.skate.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/ansatte")
@Api(tags = "Ansatte")
public class AnsattController {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    List<Ansatt> ansatte;

    @PostConstruct
    public void init() throws ParseException {
        ansatte = new ArrayList<>();

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM-yyyy");
        Date fodselsdato = dateFormat.parse("27/12-1971");

        Ansatt ole = new Ansatt(new Personnavn("Ole", "Olsen"), Kjonn.MANN, Landkode.NO, fodselsdato, Sivilstand.ENKE_ELLER_ENKEMANN);
        ole.setIdentifikator(new Identifikator("fodselsnummer", "12345678901"));

        Ansatt mari = new Ansatt(new Personnavn("Mari", "Hansen"), Kjonn.KVINNE, Landkode.NO, fodselsdato, Sivilstand.GIFT);
        mari.setIdentifikator(new Identifikator("ansattnummer", "123"));

        Ansatt trine = new Ansatt(new Personnavn("Trine", "Johansen"), Kjonn.KVINNE, Landkode.SE, fodselsdato, Sivilstand.GJENLEVENDE_PARTNER);
        Ansatt line = new Ansatt(new Personnavn("Line", "Svendsen"), Kjonn.KVINNE, Landkode.NO, fodselsdato, Sivilstand.SKILT);
        Ansatt pal = new Ansatt(new Personnavn("Pål", "Persen"), Kjonn.MANN, Landkode.NO, fodselsdato, Sivilstand.GIFT);
        ansatte.add(ole);
        ansatte.add(mari);
        ansatte.add(trine);
        ansatte.add(line);
        ansatte.add(pal);
    }

    @RabbitListener(queues = "vaf-ut")
    public void receiveResponse(byte[] content) {
        log.info("Response: {}", new String(content));
    }

    @ApiOperation("Henter alle ansatte")
    @RequestMapping(method = RequestMethod.GET)
    public List<Ansatt> hentAnsatte(@RequestParam(required = false) final String navn) {
        log.info("hentAnsatte - navn: {}", navn);
        if (navn == null) {
            return ansatte;
        } else {
            return ansatte.stream().filter(ansatt -> {
                String fornavn = ansatt.getNavn().getFornavn().toLowerCase();
                String etternavn = ansatt.getNavn().getEtternavn().toLowerCase();
                return (navn.equals(fornavn) || navn.equals(etternavn));
            }).collect(Collectors.toList());
        }
    }

    @RequestMapping(value = "/{identifikatortype}/{id}", method = RequestMethod.GET)
    public Ansatt hentAnsatt(@PathVariable String identifikatortype, @PathVariable String id) {
        log.info("hentAnsatt - identifikatorType: {}, id: {}", identifikatortype, id);

        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setCorrelationId(UUID.randomUUID().toString().getBytes());
        rabbitTemplate.setReplyTimeout(30000);
        Message response = rabbitTemplate.sendAndReceive("vaf-inn", new Message(("{\"identifikatortype\":\"" + identifikatortype + "\", \"id\":\"" + id + "\"}").getBytes(), messageProperties));
        System.out.println(new String(response.getBody()));

        Optional<Ansatt> ansatt = findAnsatt(identifikatortype, id);
        if (ansatt.isPresent())
            return ansatt.get();

        return new Ansatt();
    }

    private Optional<Ansatt> findAnsatt(String identifikatortype, String id) {
        return ansatte.stream().filter(ansatt -> {
            if (ansatt.getIdentifikator() != null) {
                String type = ansatt.getIdentifikator().getIdentifikatorType();
                String verdi = ansatt.getIdentifikator().getIdentifikatorVerdi();

                return (identifikatortype.equals(type) && id.equals(verdi));
            }
            return false;
        }).findFirst();
    }

    @RequestMapping(method = RequestMethod.PUT)
    public void oppdaterAnsatt(@RequestBody Ansatt ansatt) {
        log.info("oppdaterAnsatt - {}", ansatt);

        String type = ansatt.getIdentifikator().getIdentifikatorType();
        String verdi = ansatt.getIdentifikator().getIdentifikatorVerdi();
        Optional<Ansatt> existingAnsatt = findAnsatt(type, verdi);
        if (existingAnsatt.isPresent()) {
            KontaktInformasjon kontaktInformasjon = existingAnsatt.get().getKontaktInformasjon();
            if (kontaktInformasjon == null) {
                kontaktInformasjon = new KontaktInformasjon();
                existingAnsatt.get().setKontaktInformasjon(kontaktInformasjon);
            }

            kontaktInformasjon.setEpostadresse(ansatt.getKontaktInformasjon().getEpostadresse());
        }
    }
}
