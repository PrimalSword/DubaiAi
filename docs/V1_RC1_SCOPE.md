# Orbis Trade AI v1.0 RC1

Versão candidata para validação em conta demo durante uma semana.

## Os 12 critérios incorporados

1. Validação visual atual da tela e bloqueio de falsos positivos.
2. Candle Tracker temporal.
3. Motor de calibração pixel/preço por âncoras OCR.
4. Contexto: ativo, mercado, payout e vencimento.
5. Ciclo auditável de sinais com resultados WIN, LOSS, DRAW e INVALIDATED.
6. Break-even, valor esperado, margem e amostra.
7. Regimes de mercado.
8. Separação entre score técnico e probabilidade calibrada.
9. Política de risco sem martingale e bloqueios objetivos.
10. Estatísticas separadas por OTC e mercado aberto.
11. Avaliação walk-forward treino/validação/teste.
12. Banco SQLite v3 e exportação CSV completa.

## Limites honestos da RC1

A calibração de preço depende de âncoras OCR confiáveis. A conclusão automática de uma operação depende de preço e vencimento corretamente reconhecidos; durante a semana de validação os resultados podem ser confirmados ou invalidados manualmente para auditoria. Screenshots de entrada e saída estão previstos no esquema de banco, mas sua persistência deve ser validada em hardware antes de ser tratada como evidência definitiva.

O aplicativo não executa operações e deve permanecer em conta demo.

## Protocolo semanal

- alternar OTC e mercado aberto;
- testar manhã, tarde e noite;
- testar diferentes ativos, payouts e vencimentos;
- testar zoom aberto, intermediário e fechado;
- alternar entre Quotex e outras telas para verificar falsos positivos;
- classificar resultados e invalidar leituras incorretas;
- exportar o CSV ao final de cada dia;
- não alterar parâmetros durante a semana.
