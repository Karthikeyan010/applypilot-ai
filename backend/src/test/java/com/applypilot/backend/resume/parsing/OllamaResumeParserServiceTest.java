package com.applypilot.backend.resume.parsing;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic HTTP provider contract tests. These do not measure live model accuracy. */
class OllamaResumeParserServiceTest {
    final ObjectMapper json = new ObjectMapper();
    HttpServer server; String reply; JsonNode sent; int status=200;
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/chat", exchange -> {
            sent=json.readTree(exchange.getRequestBody().readAllBytes());
            byte[] bytes=reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    OllamaResumeParserService parser(){return new OllamaResumeParserService(new OllamaProperties(
        "http://127.0.0.1:"+server.getAddress().getPort(),"synthetic-model",2,16000,16384),
        Validation.buildDefaultValidatorFactory().getValidator());}
    ObjectNode empty(JsonNode schema) {
        ObjectNode object=json.createObjectNode();
        schema.get("properties").fields().forEachRemaining(field -> {
            if(field.getValue().get("type").isTextual()&&field.getValue().get("type").asText().equals("array"))object.putArray(field.getKey());
            else object.putNull(field.getKey());
        });return object;
    }
    ObjectNode grouped() {
        var profile=empty(ResumeJsonSchema.schema());profile.put("fullName","Jordan Lee");profile.put("email","jordan@example.com");
        var project=empty(ResumeJsonSchema.schema().at("/properties/projects/items"));project.put("name","Scalable Bookstore Microservices Platform");
        project.putArray("technologies").add("Java").add("Spring Boot").add("PostgreSQL");
        project.putArray("description").add("Built an inventory service with Java.").add("Added resilient checkout workflows.").add("Deployed services with Docker.");
        ((ArrayNode)profile.get("projects")).add(project);
        var job=empty(ResumeJsonSchema.schema().at("/properties/experience/items"));job.put("company","Example Co");job.put("jobTitle","Engineer");
        job.putArray("responsibilities").add("Built REST APIs.").add("Reviewed code with the team.").add("Improved automated tests.");
        ((ArrayNode)profile.get("experience")).add(job);return profile;
    }
    void respond(JsonNode profile) throws Exception {reply=json.writeValueAsString(Map.of("done",true,"message",Map.of("content",json.writeValueAsString(profile))));}
    String fixture(String name) throws Exception {try(var input=getClass().getResourceAsStream("/cvs/"+name+".txt")){return new String(input.readAllBytes(),StandardCharsets.UTF_8);}}
    @ParameterizedTest @ValueSource(strings={"standard","wrapped"})
    void preservesGroupedBulletsAcrossLayoutsAndSendsSchema(String layout) throws Exception {
        respond(grouped());String source=fixture(layout);var result=parser().parse(source);
        assertEquals(1,result.profile().getProjects().size());assertEquals(3,result.profile().getProjects().getFirst().getDescription().size());
        assertEquals(1,result.profile().getExperience().size());assertEquals(3,result.profile().getExperience().getFirst().getResponsibilities().size());
        assertEquals(source,result.profile().getOriginalText());assertEquals(source,result.profile().getUnmatchedText());
        assertEquals(1,result.warnings().size());assertEquals("synthetic-model",sent.path("model").asText());
        assertEquals(ResumeJsonSchema.schema(),sent.path("format"));assertFalse(sent.path("stream").asBoolean());
        assertEquals(source,sent.at("/messages/1/content").asText());assertTrue(sent.at("/messages/0/content").asText().contains("ALL its description"));
    }
    @Test void removesHallucinationsAndFlagsSourceEvidence() throws Exception {
        var profile=grouped();profile.put("phone","+44 999 123456");((ArrayNode)profile.get("skills")).add("Kubernetes");
        ((ObjectNode)profile.at("/projects/0")).put("originalText","invented source");respond(profile);
        var result=parser().parse(fixture("standard"));assertNull(result.profile().getPhone());assertTrue(result.profile().getSkills().isEmpty());
        assertNull(result.profile().getProjects().getFirst().getOriginalText());assertEquals(4,result.warnings().size());
    }
    @Test void rejectsMalformedMissingExtraWrongTypedAndOversizedOutput() throws Exception {
        for(String field:List.of("missing","extra","type","length")) {
            var profile=grouped();
            if(field.equals("missing"))profile.remove("projects");if(field.equals("extra"))profile.put("secret","extra");
            if(field.equals("type"))profile.put("skills","Java");if(field.equals("length"))profile.put("fullName","x".repeat(1001));
            respond(profile);assertThrows(ResumeParsingException.class,()->parser().parse("Jordan Lee"));
        }
        for(String invalid:List.of("{malformed","{\"done\":true,\"message\":{\"content\":\"null\"}}","{\"done\":false}")){
            reply=invalid;assertThrows(ResumeParsingException.class,()->parser().parse("Jordan Lee"));
        }
    }
    @Test void unavailableEmptyAndLongInputsNeverFallBack() {
        status=404;reply="private model response";
        var error=assertThrows(ResumeParsingException.class,()->parser().parse("Jordan Lee"));assertTrue(error.getMessage().contains("Ollama is unavailable"));assertFalse(error.getMessage().contains("private"));
        assertThrows(ResumeParsingException.class,()->parser().parse(""));assertThrows(ResumeParsingException.class,()->parser().parse("x".repeat(16001)));
    }
    @Test void timeoutIncludesSlowResponseBody() {
        server.removeContext("/api/chat");server.createContext("/api/chat",exchange->{
            exchange.getRequestBody().readAllBytes();exchange.sendResponseHeaders(200,1000);
            try{Thread.sleep(3000);}catch(InterruptedException e){Thread.currentThread().interrupt();}exchange.close();
        });
        var parser=parser();long start=System.nanoTime();assertThrows(ResumeParsingException.class,()->parser.parse("Jordan Lee"));
        assertTrue((System.nanoTime()-start)/1_000_000<2900);
    }
}
