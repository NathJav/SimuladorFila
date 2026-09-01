import java.util.Comparator;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Aluna: Nathalie Jordão de Ávila 
 * Simulador de eventos discretos para filas G/G/c/K.
 *
 * Neste trabalho:
 * - c e o numero de servidores;
 * - K = 5 e a capacidade total (atendimento + espera);
 * - os intervalos de chegada e de atendimento seguem distribuicao uniforme;
 * - a fila inicia vazia e a primeira chegada ocorre no tempo 3,0;
 * - a execucao termina imediatamente depois de utilizar o aleatorio 100.000.
 */
public class SimuladorFila {
    private static final int CAPACIDADE = 5;
    private static final int LIMITE_ALEATORIOS = 100_000;
    private static final double PRIMEIRA_CHEGADA = 3.0;
    private static final long SEMENTE = 42L;

    private enum TipoEvento {
        CHEGADA,
        SAIDA
    }

    private static final class Evento {
        final double tempo;
        final TipoEvento tipo;
        final long ordem;

        Evento(double tempo, TipoEvento tipo, long ordem) {
            this.tempo = tempo;
            this.tipo = tipo;
            this.ordem = ordem;
        }
    }

    private static final class Resultado {
        final int servidores;
        final double chegadaMin;
        final double chegadaMax;
        final double atendimentoMin;
        final double atendimentoMax;
        final double[] tempos;
        final int perdas;
        final int aleatorios;
        final double tempoGlobal;

        Resultado(
                int servidores,
                double chegadaMin,
                double chegadaMax,
                double atendimentoMin,
                double atendimentoMax,
                double[] tempos,
                int perdas,
                int aleatorios,
                double tempoGlobal) {
            this.servidores = servidores;
            this.chegadaMin = chegadaMin;
            this.chegadaMax = chegadaMax;
            this.atendimentoMin = atendimentoMin;
            this.atendimentoMax = atendimentoMax;
            this.tempos = tempos;
            this.perdas = perdas;
            this.aleatorios = aleatorios;
            this.tempoGlobal = tempoGlobal;
        }
    }

    private static final class GeradorUniforme {
        private final Random random;
        private int usados;

        GeradorUniforme(long semente) {
            random = new Random(semente);
        }

        double proximo(double minimo, double maximo) {
            if (usados >= LIMITE_ALEATORIOS) {
                throw new IllegalStateException("O limite de aleatorios foi atingido.");
            }
            usados++;
            return minimo + (maximo - minimo) * random.nextDouble();
        }

        boolean terminou() {
            return usados == LIMITE_ALEATORIOS;
        }
    }

    public static Resultado simular(
            int servidores,
            double chegadaMin,
            double chegadaMax,
            double atendimentoMin,
            double atendimentoMax) {

        if (servidores < 1 || servidores > CAPACIDADE) {
            throw new IllegalArgumentException("Quantidade de servidores invalida.");
        }
        if (chegadaMin > chegadaMax || atendimentoMin > atendimentoMax) {
            throw new IllegalArgumentException("Intervalo invalido.");
        }

        GeradorUniforme gerador = new GeradorUniforme(SEMENTE);
        double[] temposPorEstado = new double[CAPACIDADE + 1];
        int clientesNoSistema = 0;
        int perdas = 0;
        double tempoGlobal = 0.0;
        long ordemEvento = 0;

        // Em empate, processa SAIDA antes de CHEGADA. A ordem de insercao
        // resolve empates entre eventos do mesmo tipo.
        Comparator<Evento> comparador = Comparator
                .comparingDouble((Evento e) -> e.tempo)
                .thenComparingInt(e -> e.tipo == TipoEvento.SAIDA ? 0 : 1)
                .thenComparingLong(e -> e.ordem);
        PriorityQueue<Evento> eventos = new PriorityQueue<>(comparador);
        eventos.add(new Evento(PRIMEIRA_CHEGADA, TipoEvento.CHEGADA, ordemEvento++));

        while (!gerador.terminou()) {
            Evento evento = eventos.remove();

            // O sistema permaneceu no estado atual desde o ultimo evento.
            temposPorEstado[clientesNoSistema] += evento.tempo - tempoGlobal;
            tempoGlobal = evento.tempo;

            if (evento.tipo == TipoEvento.CHEGADA) {
                boolean entrou = clientesNoSistema < CAPACIDADE;
                if (entrou) {
                    clientesNoSistema++;
                } else {
                    perdas++;
                }

                // Agenda primeiro a proxima chegada. Essa ordem fixa tambem
                // torna a sequencia de aleatorios totalmente reproduzivel.
                double intervaloChegada = gerador.proximo(chegadaMin, chegadaMax);
                eventos.add(new Evento(
                        tempoGlobal + intervaloChegada,
                        TipoEvento.CHEGADA,
                        ordemEvento++));

                if (gerador.terminou()) {
                    break;
                }

                // Se o cliente entrou diretamente em um servidor livre,
                // agenda a conclusao do seu atendimento.
                if (entrou && clientesNoSistema <= servidores) {
                    double duracaoAtendimento =
                            gerador.proximo(atendimentoMin, atendimentoMax);
                    eventos.add(new Evento(
                            tempoGlobal + duracaoAtendimento,
                            TipoEvento.SAIDA,
                            ordemEvento++));
                }
            } else {
                clientesNoSistema--;

                // Se ainda ha pelo menos 'servidores' clientes no sistema,
                // um cliente que esperava inicia atendimento neste instante.
                if (clientesNoSistema >= servidores) {
                    double duracaoAtendimento =
                            gerador.proximo(atendimentoMin, atendimentoMax);
                    eventos.add(new Evento(
                            tempoGlobal + duracaoAtendimento,
                            TipoEvento.SAIDA,
                            ordemEvento++));
                }
            }
        }

        return new Resultado(
                servidores,
                chegadaMin,
                chegadaMax,
                atendimentoMin,
                atendimentoMax,
                temposPorEstado,
                perdas,
                gerador.usados,
                tempoGlobal);
    }

    private static void imprimir(Resultado resultado) {
        System.out.printf(
                Locale.US,
                "%nG/G/%d/5 | chegadas %.1f...%.1f | atendimento %.1f...%.1f%n",
                resultado.servidores,
                resultado.chegadaMin,
                resultado.chegadaMax,
                resultado.atendimentoMin,
                resultado.atendimentoMax);
        System.out.println("Estado | Tempo acumulado | Probabilidade");

        double somaProbabilidades = 0.0;
        double somaTempos = 0.0;
        for (int estado = 0; estado < resultado.tempos.length; estado++) {
            double probabilidade = resultado.tempos[estado] / resultado.tempoGlobal;
            somaProbabilidades += probabilidade;
            somaTempos += resultado.tempos[estado];
            System.out.printf(
                    Locale.US,
                    "%6d | %15.6f | %12.6f%%%n",
                    estado,
                    resultado.tempos[estado],
                    probabilidade * 100.0);
        }

        System.out.printf(Locale.US, "Total  | %15.6f | %12.6f%%%n", somaTempos,
                somaProbabilidades * 100.0);
        System.out.printf(Locale.US, "Clientes perdidos: %d%n", resultado.perdas);
        System.out.printf(Locale.US, "Tempo global: %.6f%n", resultado.tempoGlobal);
        System.out.printf(Locale.US, "Aleatorios utilizados: %d%n", resultado.aleatorios);
        System.out.printf(Locale.US, "Semente: %d%n", SEMENTE);
    }

    public static void main(String[] args) {
        System.out.println("ESPECIFICACAO PRINCIPAL DO ENUNCIADO");
        imprimir(simular(1, 3.0, 5.0, 4.0, 5.0));
        imprimir(simular(2, 3.0, 5.0, 4.0, 5.0));

        System.out.println("\nINTERVALOS QUE APARECEM NOS CAMPOS 2 E 3");
        imprimir(simular(1, 2.0, 5.0, 3.0, 5.0));
        imprimir(simular(2, 2.0, 5.0, 3.0, 5.0));
    }
}
