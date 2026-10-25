package io.github.tetzdesen.cardapioru.notificacao;

import java.util.List;

/**
 * Um lugar para onde o aviso vai -- a estrategia que o {@link DisparoTransacional}
 * aplica a cada canal configurado, sem saber se do outro lado esta o Telegram ou
 * o WhatsApp.
 *
 * <p>As pecas chegam em HTML, porque e desse texto que sai a assinatura do
 * estado; quem fala outra marcacao converte na hora de enviar.
 */
public interface Canal {

    /** Nome do Telegram, cujas entradas no estado ficam sem sufixo. */
    String TELEGRAM = "telegram";

    /** Identifica o canal no estado e no log: "telegram", "whatsapp". */
    String nome();

    /** Sem configuracao o canal fica de fora do disparo, em vez de falhar. */
    boolean configurado();

    /**
     * Envia e devolve as refeicoes que o canal confirmou ter recebido.
     *
     * @throws FalhaDeEnvio com as refeicoes que chegaram antes da falha
     */
    List<String> enviar(List<Peca> pecas);
}
