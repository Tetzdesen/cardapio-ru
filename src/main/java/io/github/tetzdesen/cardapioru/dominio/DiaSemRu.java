package io.github.tetzdesen.cardapioru.dominio;

import java.time.LocalDate;
import java.util.List;

/** Um dia em que as {@code unidades} nao atendem refeicao nenhuma. */
public record DiaSemRu(LocalDate dia, List<String> unidades) {
}
