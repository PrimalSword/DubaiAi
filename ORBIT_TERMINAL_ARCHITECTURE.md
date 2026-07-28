# Orbis Decision Terminal — arquitetura congelada

## Propósito

Aplicativo Android de apoio à decisão para operação manual em conta demo. O produto não executa ordens, não promete lucro e não usa martingale. A prioridade é reduzir operações de baixa qualidade, preservar risco e produzir evidência auditável.

## Princípio operacional

A decisão passa obrigatoriamente por esta cadeia, sem atalhos:

1. captura e validação da tela atual;
2. rastreamento temporal dos candles;
3. memória multi-timeframe;
4. estrutura de mercado e regime;
5. seleção de playbook;
6. avaliação da qualidade de entrada;
7. probabilidade condicionada e expectativa econômica;
8. motor de veto;
9. política de risco da sessão;
10. alerta explicável;
11. diário e auditoria;
12. análise comparativa humano × sistema.

## Escopo fechado da primeira release

### Mercado
- Mercado aberto e OTC, com estatísticas separadas.
- Timeframes de contexto (15m), estrutura (5m) e entrada (1m).
- A seleção do timeframe de captura é explícita; o aplicativo guarda a última leitura validada de cada escala.

### Playbooks
- Continuação por pullback.
- Rompimento com expansão.
- Retorno à média em lateralidade.
- Reversão em extremo.

Nenhum playbook universal será criado. O regime habilita ou bloqueia cada playbook.

### Etapas do setup
- CONTEXTO
- FORMANDO
- ARMADO
- VÁLIDO
- PERDIDO
- INVALIDADO

### Motores
- `TemporalCandleTracker`: estabiliza candles entre frames.
- `MarketStructureEngine`: pivôs, direção estrutural e espaço até obstáculo.
- `RegimeEngine`: tendência, lateralidade, compressão, expansão, reversão e ruído.
- `PlaybookEngine`: escolhe apenas o playbook compatível.
- `EntryQualityEngine`: atraso, extensão, candle/ATR, distância das médias e espaço disponível.
- `ProbabilityEngine`: probabilidade suavizada por amostra, modalidade, regime e playbook.
- `VetoEngine`: bloqueia por dados, contexto, payout, qualidade, expectativa e risco.
- `SessionRiskEngine`: risco fixo, stop diário, meta, limite de operações e pausa após perdas.
- `JournalStore`: auditoria local, resultado manual, decisão humana e decisão do sistema.

### Modos de uso
- OBSERVADOR: registra sem sugerir execução.
- ASSISTIDO: exibe decisão e veto antes da escolha humana.
- CEGO: registra a escolha humana antes de revelar a leitura do sistema.

### Saídas
O overlay sempre mostra:
- validade da tela;
- modalidade e timeframe;
- regime;
- playbook e etapa;
- direção candidata;
- confluência;
- probabilidade, break-even e EV;
- operação permitida ou principal veto.

### Persistência e análise
- SQLite próprio da versão Terminal.
- CSV completo.
- Estatísticas por mercado, playbook, regime, horário e modo de uso.
- Métricas de expectativa, drawdown, profit factor, aderência e perdas evitadas.

## Restrições de produto

- `applicationId` independente para coexistir com o Orbis Trade AI RC1.
- Nenhum clique ou execução automática na corretora.
- Nenhuma elevação de risco após perda.
- Nenhuma probabilidade será apresentada como garantia.
- Sem reestruturação da arquitetura durante esta release; somente correções de compilação e defeitos objetivos.

## Critérios de aceite

- Testes unitários dos motores de estrutura, decisão e risco.
- APK Android gerado pelo GitHub Actions.
- Instalação paralela à RC1.
- Tela sem gráfico não pode produzir decisão válida.
- Mercado aberto e OTC não compartilham amostras.
- Setup sem expectativa positiva deve permanecer bloqueado.
- Limites da sessão devem prevalecer sobre qualquer sinal técnico.
