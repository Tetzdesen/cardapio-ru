package io.github.tetzdesen.cardapioru;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FixturesTest {

    @ParameterizedTest
    @ValueSource(strings = {Fixtures.COMPLETA, Fixtures.VAZIA, Fixtures.QUEBRADA})
    void carregaCadaFixture(String nome) {
        assertThat(Fixtures.ler(nome)).isNotEmpty();
    }

    /**
     * Os md5 sao os das paginas como estavam em tests/fixtures/ na suite Python.
     * Se um deles mudar, a comparacao com a saida do ru_bot.py deixa de valer.
     */
    @Test
    void nenhumByteMudouNaMigracao() {
        assertThat(md5(Fixtures.bytes(Fixtures.COMPLETA)))
                .isEqualTo("9e7ae3372e008ecc0999b8252f7a93b1");
        assertThat(md5(Fixtures.bytes(Fixtures.VAZIA)))
                .isEqualTo("b8eedff9248d44698e466327f1198378");
        assertThat(md5(Fixtures.bytes(Fixtures.QUEBRADA)))
                .isEqualTo("95f434adc8bb7d81890bb2c48f79d2ee");
    }

    private static String md5(byte[] dados) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(dados));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
