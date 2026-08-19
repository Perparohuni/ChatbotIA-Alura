package br.com.alura.ecomart.chatbot.web.controller;

import br.com.alura.ecomart.chatbot.domain.service.ChatBotService;
import br.com.alura.ecomart.chatbot.web.dto.PerguntaDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@Controller
@RequestMapping({"/", "chat"})
public class ChatController {

    private static final String PAGINA_CHAT = "chat";

    @Autowired
    private ChatBotService service;

    @GetMapping
    public String carregarPaginaChatbot() {
        return PAGINA_CHAT;
    }

    @PostMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public StreamingResponseBody responderPergunta(@RequestBody PerguntaDto dto) {
        return outputStream -> service.responderPergunta(dto.pergunta(),
                pedaco -> {
                    try {
                        outputStream.write(pedaco.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }


    @GetMapping("limpar")
    public String limparConversa() {
        service.apagarHistorico();
        return "redirect:/chat";
    }

}
