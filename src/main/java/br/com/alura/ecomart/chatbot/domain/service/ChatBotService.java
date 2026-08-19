package br.com.alura.ecomart.chatbot.domain.service;

import br.com.alura.ecomart.chatbot.infra.openai.DadosRequisicaoChatCompletion;
import br.com.alura.ecomart.chatbot.infra.openai.OpenAIClientBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class ChatBotService {
    @Autowired
    private OpenAIClientBase client;

    private String ultimoResponseId;

    public void responderPergunta(String pergunta, Consumer<String>
            aoReceberTexto) {
        String prompt = "Você é Carlos, e gosta muito de vitamina de banana sabor tutti-frutti";
        DadosRequisicaoChatCompletion dados = new DadosRequisicaoChatCompletion(prompt, pergunta, ultimoResponseId);
        client.enviarRequisicaoChatCompletionStream(dados, aoReceberTexto, novoResponseId -> this.ultimoResponseId = novoResponseId);
    }

    public void apagarHistorico() {
        ultimoResponseId = null;
        client.apagarHistorico();
    }
}