package br.com.alura.ecomart.chatbot.infra.openai;

import br.com.alura.ecomart.chatbot.domain.DadosCalculoFrete;
import br.com.alura.ecomart.chatbot.domain.service.CalculadorDeFrete;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.models.ChatModel;
import com.openai.models.responses.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class OpenAIClientBase {

    private final OpenAIClient client;
    private final CalculadorDeFrete calculadorDeFrete;
    private final ObjectMapper objectMapper;

    private String previousResponseId;


    public OpenAIClientBase(@Value("${app.openai.api.key}") String apiKey, CalculadorDeFrete calculadorDeFrete, ObjectMapper objectMapper) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.calculadorDeFrete = calculadorDeFrete;
        this.objectMapper = objectMapper;
    }

    public void apagarHistorico() {
        previousResponseId = null;
    }

    public void enviarRequisicaoChatCompletionStream(
            DadosRequisicaoChatCompletion dados,
            Consumer<String> aoReceberTexto,
            Consumer<String> aoFinalizarResponse
    ) {
        var builder = ResponseCreateParams.builder()
                .model(ChatModel.GPT_4_1_NANO)
                .instructions(dados.promptSistema())
                .addTool(toolCalcularFrete())
                .parallelToolCalls(false)
                .input(dados.promptUsuario());
        if(dados.previousResponseId() != null) {
            builder.previousResponseId(dados.previousResponseId());
        }

        var functionCalls = new ArrayList<ResponseFunctionToolCall>();
        var responseId = new AtomicReference<String>();

        try (StreamResponse<ResponseStreamEvent> streamResponse =
                     client.responses().createStreaming(builder.build())) {
            streamResponse.stream().forEach(event ->
                {
                    event.outputTextDelta().ifPresent(delta -> aoReceberTexto.accept(delta.delta()));
                    event.completed().ifPresent(completed -> {
                        var response = completed.response();
                        responseId.set(response.id());
                        aoFinalizarResponse.accept(response.id());

                        response.output().stream()
                                .filter(ResponseOutputItem::isFunctionCall)
                                .map(ResponseOutputItem::asFunctionCall)
                                .forEach(functionCalls::add);
                    });
                });
        }
        if(!functionCalls.isEmpty()) {
            executarFunctionCalls(functionCalls, responseId.get(),aoReceberTexto, aoFinalizarResponse);
        }
    }

    private ResponseInputItem executarFunctionCall(ResponseFunctionToolCall
                                                           chamada) {
        if (!chamada.name().equals("calcular_frete")) {
            throw new IllegalArgumentException("Função desconhecida: " +
                    chamada.name());
        }

        try {
            var dadosFrete = objectMapper.readValue(
                    chamada.arguments(),
                    DadosCalculoFrete.class
            );

            var valorFrete = calculadorDeFrete.calcular(dadosFrete);

            var resultado = objectMapper.writeValueAsString(Map.of(
                    "valorFrete", valorFrete,
                    "moeda", "BRL"
            ));

            var functionOutput = ResponseInputItem.FunctionCallOutput.builder()
                    .callId(chamada.callId())
                    .output(resultado)
                    .build();

            return ResponseInputItem.ofFunctionCallOutput(functionOutput);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao executar calcular_frete", e);
        }
    }


    private void executarFunctionCalls(
            List<ResponseFunctionToolCall> chamadas,
            String responseId,
            Consumer<String> aoReceberTexto,
            Consumer<String> aoFinalizarResponse
    ) {
        var outputs = chamadas.stream()
                .map(this::executarFunctionCall)
                .toList();

        enviarResultadosDasFuncoesParaOpenAI(
                responseId,
                outputs,
                aoReceberTexto,
                aoFinalizarResponse
        );
    }


    private void enviarResultadosDasFuncoesParaOpenAI(
            String previousResponseId,
            List<ResponseInputItem> outputs,
            Consumer<String> aoReceberTexto,
            Consumer<String> aoFinalizarResponse
    ) {
        var params = ResponseCreateParams.builder()
                .model(ChatModel.GPT_4_1_NANO)
                .previousResponseId(previousResponseId)
                .inputOfResponse(outputs)
                .build();

        try (StreamResponse<ResponseStreamEvent> streamResponse =
                     client.responses().createStreaming(params)) {

            streamResponse.stream().forEach(event -> {
                event.outputTextDelta().ifPresent(delta ->
                        aoReceberTexto.accept(delta.delta())
                );

                event.completed().ifPresent(completed ->
                        aoFinalizarResponse.accept(completed.response().id())
                );
            });
        }
    }




    private FunctionTool toolCalcularFrete() {
        var parametros = FunctionTool.Parameters.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        "quantidadeProdutos", Map.of(
                                "type", "integer",
                                "description", "Quantidade de produtos no carrinho"
                        ),
                        "uf", Map.of(
                                "type", "string",
                                "description", "UF de entrega",
                                "enum", List.of(
                                        "AC", "AL", "AP", "AM", "BA", "CE", "DF",
                                        "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB",
                                        "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR",
                                        "SC", "SP", "SE", "TO"
                                )
                        )
                )))
                .putAdditionalProperty("required", JsonValue.from(List.of(
                        "quantidadeProdutos",
                        "uf"
                )))
                .putAdditionalProperty("additionalProperties",
                        JsonValue.from(false))
                .build();

        return FunctionTool.builder()
                .name("calcular_frete")
                .description("Calcula o valor do frete com base na quantidade de produtos e na UF de entrega.")
                                .parameters(parametros)
                                .strict(true)
                                .build();
    }

}